package com.medkernel.engine.integration.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.medkernel.engine.integration.domain.IntegrationAdapter;

/**
 * REST/FHIR/Webhook/WebService 的标准 HTTP 连接器。
 *
 * <p>适配器配置只保存连接元数据。鉴权类请求头必须使用 {@code ${ENV_NAME}}
 * 引用运行环境变量，禁止把密钥明文写入数据库。
 */
@Component
public class HttpIntegrationConnector implements IntegrationConnector {

    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of("REST", "FHIR", "WEBHOOK", "WEBSERVICE");
    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
        "host", "content-length", "transfer-encoding", "connection");
    private static final Pattern ENV_REFERENCE = Pattern.compile("^\\$\\{([A-Z][A-Z0-9_]*)}$");
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2000;
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 5000;
    private static final int MIN_TIMEOUT_MS = 200;
    private static final int MAX_TIMEOUT_MS = 30000;

    private final ObjectMapper objectMapper;
    private final Environment environment;

    public HttpIntegrationConnector(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    public boolean supports(IntegrationAdapter adapter) {
        return adapter != null
            && adapter.protocolType() != null
            && SUPPORTED_PROTOCOLS.contains(adapter.protocolType().trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public IntegrationConnectorValidation validate(IntegrationAdapter adapter) {
        try {
            parse(adapter);
            return IntegrationConnectorValidation.success();
        } catch (IllegalArgumentException exception) {
            return IntegrationConnectorValidation.invalid(exception.getMessage());
        }
    }

    @Override
    public IntegrationConnectorHealth checkHealth(IntegrationAdapter adapter) {
        HttpConnectorConfig config;
        try {
            config = parse(adapter);
        } catch (IllegalArgumentException exception) {
            return new IntegrationConnectorHealth("MISCONFIGURED", 0L, exception.getMessage());
        }
        long started = System.nanoTime();
        try {
            HttpResponse<Void> response = client(config).send(
                requestBuilder(config, config.healthUri())
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.discarding());
            long rttMs = elapsedMillis(started);
            if (isSuccess(response.statusCode())) {
                return new IntegrationConnectorHealth("HEALTHY", rttMs, "HTTP 探活成功");
            }
            return new IntegrationConnectorHealth(
                "NOT_CONNECTED", rttMs, "HTTP 探活返回状态 " + response.statusCode());
        } catch (IOException exception) {
            return new IntegrationConnectorHealth("NOT_CONNECTED", elapsedMillis(started), safeMessage(exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new IntegrationConnectorHealth("NOT_CONNECTED", elapsedMillis(started), "HTTP 探活被中断");
        }
    }

    @Override
    public IntegrationDeliveryResult deliver(
            IntegrationAdapter adapter,
            JsonNode payload,
            String messageId,
            String traceId,
            Map<String, String> runtimeHeaders) {
        HttpConnectorConfig config;
        try {
            config = parse(adapter);
        } catch (IllegalArgumentException exception) {
            return IntegrationDeliveryResult.failed(false, exception.getMessage());
        }
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = requestBuilder(config, config.outboundUri())
                .header("Content-Type", "application/json")
                .header("X-MedKernel-Message-Id", required(messageId, "消息 ID"))
                .POST(HttpRequest.BodyPublishers.ofString(body));
            if (traceId != null && !traceId.isBlank()) {
                builder.header("X-MedKernel-Trace-Id", traceId.trim());
            }
            if (runtimeHeaders != null) {
                runtimeHeaders.forEach(builder::header);
            }
            HttpResponse<Void> response = client(config).send(
                builder.build(),
                HttpResponse.BodyHandlers.discarding());
            if (isSuccess(response.statusCode())) {
                return IntegrationDeliveryResult.success();
            }
            return IntegrationDeliveryResult.failed(
                true, "HTTP 投递返回状态 " + response.statusCode());
        } catch (JsonProcessingException exception) {
            return IntegrationDeliveryResult.failed(false, "出站载荷无法序列化");
        } catch (IOException exception) {
            return IntegrationDeliveryResult.failed(false, safeMessage(exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return IntegrationDeliveryResult.failed(false, "HTTP 投递被中断");
        }
    }

    private HttpClient client(HttpConnectorConfig config) {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    private HttpRequest.Builder requestBuilder(HttpConnectorConfig config, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(config.requestTimeoutMs()))
            .header("Accept", "application/json");
        config.headers().forEach(builder::header);
        return builder;
    }

    private HttpConnectorConfig parse(IntegrationAdapter adapter) {
        if (!supports(adapter)) {
            throw new IllegalArgumentException("当前协议没有 HTTP 连接器");
        }
        JsonNode root = parseJson(adapter.configJson());
        URI baseUri = parseBaseUri(text(root, "baseUrl"));
        String healthPath = path(root, "healthPath", "/");
        String outboundPath = path(root, "outboundPath", "/");
        int connectTimeoutMs = timeout(root, "connectTimeoutMs", DEFAULT_CONNECT_TIMEOUT_MS);
        int requestTimeoutMs = timeout(root, "requestTimeoutMs", DEFAULT_REQUEST_TIMEOUT_MS);
        return new HttpConnectorConfig(
            endpoint(root, "healthUrl", baseUri, healthPath),
            endpoint(root, "outboundUrl", baseUri, outboundPath),
            connectTimeoutMs,
            requestTimeoutMs,
            headers(root.path("headers")));
    }

    private JsonNode parseJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("HTTP 适配器缺少连接配置");
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            if (!root.isObject()) {
                throw new IllegalArgumentException("适配器连接配置必须是 JSON 对象");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("适配器连接配置不是合法 JSON");
        }
    }

    private URI parseBaseUri(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("HTTP 适配器必须配置 baseUrl");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("baseUrl 不是合法 URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("baseUrl 只允许 http 或 https");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("baseUrl 不允许用户信息或片段，且必须包含主机");
        }
        return uri;
    }

    private Map<String, String> headers(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("headers 必须是 JSON 对象");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String name = entry.getKey().trim();
            String normalized = name.toLowerCase(Locale.ROOT);
            if (name.isBlank() || FORBIDDEN_HEADERS.contains(normalized)) {
                throw new IllegalArgumentException("请求头不允许配置: " + name);
            }
            if (!entry.getValue().isTextual()) {
                throw new IllegalArgumentException("请求头值必须是字符串: " + name);
            }
            String rawValue = entry.getValue().asText();
            Matcher reference = ENV_REFERENCE.matcher(rawValue);
            if (isSensitiveHeader(normalized) && !reference.matches()) {
                throw new IllegalArgumentException("敏感请求头必须使用环境变量引用: " + name);
            }
            String value = rawValue;
            if (reference.matches()) {
                value = environment.getProperty(reference.group(1));
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("环境变量未配置: " + reference.group(1));
                }
            }
            headers.put(name, value);
        });
        return Map.copyOf(headers);
    }

    private boolean isSensitiveHeader(String normalizedName) {
        return normalizedName.contains("authorization")
            || normalizedName.contains("token")
            || normalizedName.contains("secret")
            || normalizedName.contains("api-key")
            || normalizedName.contains("apikey")
            || normalizedName.contains("cookie");
    }

    private int timeout(JsonNode root, String field, int defaultValue) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        if (!node.canConvertToInt()) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        int value = node.asInt();
        if (value < MIN_TIMEOUT_MS || value > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException(field + " 必须在 200 到 30000 毫秒之间");
        }
        return value;
    }

    private String path(JsonNode root, String field, String defaultValue) {
        String value = text(root, field);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/") || normalized.startsWith("//")) {
            throw new IllegalArgumentException(field + " 必须是以 / 开头的站内路径");
        }
        return normalized;
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException(field + " 必须是字符串");
        }
        return node.asText();
    }

    private URI resolve(URI baseUri, String path) {
        String base = baseUri.toString();
        URI normalizedBase = URI.create(base.endsWith("/") ? base : base + "/");
        return normalizedBase.resolve(path.substring(1));
    }

    private URI endpoint(JsonNode root, String field, URI baseUri, String path) {
        String absoluteUrl = text(root, field);
        return absoluteUrl == null || absoluteUrl.isBlank()
            ? resolve(baseUri, path)
            : parseBaseUri(absoluteUrl);
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(1L, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    private String safeMessage(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? "HTTP 连接失败"
            : "HTTP 连接失败: " + message;
    }

    private record HttpConnectorConfig(
        URI healthUri,
        URI outboundUri,
        int connectTimeoutMs,
        int requestTimeoutMs,
        Map<String, String> headers
    ) {
    }
}
