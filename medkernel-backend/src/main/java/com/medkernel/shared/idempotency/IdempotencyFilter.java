package com.medkernel.shared.idempotency;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.trace.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * BASE-03 平台级 Idempotency-Key 过滤器。
 *
 * <p>仅处理写方法且携带 {@code Idempotency-Key} 的请求；同租户同 key 只允许一个请求进入业务逻辑。
 * 已完成记录会重放首次成功结果，并把响应体 traceId 改写为当前请求 traceId，保持本次请求的排障闭环一致。
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAY_HEADER = "X-Idempotent-Replay";

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final HexFormat HEX = HexFormat.of();

    private final IdempotencyRepository repository;
    private final IdempotencyProperties properties;
    private final ObjectMapper mapper;

    public IdempotencyFilter(
            IdempotencyRepository repository,
            IdempotencyProperties properties,
            ObjectMapper mapper) {
        this.repository = repository;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String idempotencyKey = sanitize(request.getHeader(HEADER));
        if (!properties.enabled() || idempotencyKey == null || !WRITE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        if (idempotencyKey.isBlank()) {
            writeProblem(response, ErrorCode.BAD_REQUEST, "幂等键格式无效");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String tenantId = resolveTenantId();
        String traceId = RequestContext.snapshot().traceId();
        String requestHash = requestHash(cachedRequest);
        Instant now = Instant.now();

        var existing = repository.findActive(tenantId, idempotencyKey, now);
        if (existing.isPresent()) {
            handleExisting(response, existing.get(), requestHash);
            return;
        }

        IdempotencyRecord processing = IdempotencyRecord.processing(
            tenantId,
            idempotencyKey,
            requestHash,
            request.getMethod(),
            requestPath(request),
            traceId,
            now,
            now.plus(properties.ttl()));
        if (!repository.reserve(processing)) {
            var raced = repository.findActive(tenantId, idempotencyKey, now);
            if (raced.isPresent()) {
                handleExisting(response, raced.get(), requestHash);
                return;
            }
            writeProblem(response, ErrorCode.CONFLICT, "幂等键正在被其他请求占用");
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cachedRequest, wrappedResponse);
            if (isSuccess(wrappedResponse.getStatus())) {
                byte[] responseBody = wrappedResponse.getContentAsByteArray();
                repository.complete(processing.complete(
                    wrappedResponse.getStatus(),
                    wrappedResponse.getContentType(),
                    new String(responseBody, StandardCharsets.UTF_8),
                    sha256(responseBody),
                    traceId));
            } else {
                repository.delete(tenantId, idempotencyKey);
            }
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void handleExisting(
            HttpServletResponse response,
            IdempotencyRecord record,
            String requestHash) throws IOException {
        if (!record.requestHash().equals(requestHash)) {
            writeProblem(response, ErrorCode.CONFLICT, "幂等键已被不同请求内容使用");
            return;
        }
        if (!record.completed()) {
            writeProblem(response, ErrorCode.CONFLICT, "幂等请求正在处理中");
            return;
        }
        response.setStatus(record.responseStatus());
        response.setContentType(record.responseContentType());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(REPLAY_HEADER, "true");
        response.setHeader(TraceIdFilter.HEADER, RequestContext.snapshot().traceId());
        response.getWriter().write(rewriteTraceId(record.responseBody()));
    }

    private void writeProblem(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        ObjectNode problem = mapper.createObjectNode();
        problem.put("type", URI.create("urn:medkernel:error:" + code.code()).toString());
        problem.put("title", code.defaultMessage());
        problem.put("status", HttpStatus.valueOf(code.httpStatus()).value());
        problem.put("detail", detail);
        problem.put("code", code.code());
        problem.put("errorClass", code.errorClass().name());
        problem.put("retryable", code.retryable());
        problem.put("traceId", RequestContext.snapshot().traceId());
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), problem);
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > 128) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == ':')) {
                return "";
            }
        }
        return value;
    }

    private String resolveTenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return tenantId == null || tenantId.isBlank() ? "SYSTEM" : tenantId;
    }

    private String requestHash(CachedBodyHttpServletRequest request) {
        String material = request.getMethod() + "\n" + requestPath(request) + "\n";
        byte[] prefix = material.getBytes(StandardCharsets.UTF_8);
        byte[] body = request.body();
        byte[] joined = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, joined, 0, prefix.length);
        System.arraycopy(body, 0, joined, prefix.length, body.length);
        return sha256(joined);
    }

    private String requestPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private String rewriteTraceId(String responseBody) throws IOException {
        if (responseBody == null || responseBody.isBlank()) {
            return responseBody == null ? "" : responseBody;
        }
        try {
            var node = mapper.readTree(responseBody);
            if (node instanceof ObjectNode objectNode && objectNode.has("traceId")) {
                objectNode.put("traceId", RequestContext.snapshot().traceId());
                return mapper.writeValueAsString(objectNode);
            }
            return responseBody;
        } catch (IOException ex) {
            return responseBody;
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", ex);
        }
    }
}
