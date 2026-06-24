package com.medkernel.shared.security;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.RequestContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 浏览器 cookie 会话的 CSRF 双提交保护。
 *
 * <p>仅当请求携带登录态 cookie 且方法会改变服务器状态时校验；Bearer API 客户端不受影响。
 */
public class CsrfDoubleSubmitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(CsrfDoubleSubmitFilter.class);
    public static final String XSRF_COOKIE = "XSRF-TOKEN";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AuthCookieProperties cookieProperties;
    private final SystemConfigService configService;
    private final ObjectMapper objectMapper;

    public CsrfDoubleSubmitFilter(AuthCookieProperties cookieProperties,
                                  SystemConfigService configService,
                                  ObjectMapper objectMapper) {
        this.cookieProperties = cookieProperties;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || isLoginEndpoint(request) || !hasAuthCookie(request)) {
            chain.doFilter(request, response);
            return;
        }

        String headerToken = headerToken(request);
        if (hasMatchingCookie(request, XSRF_COOKIE, headerToken)) {
            chain.doFilter(request, response);
            return;
        }

        LOG.debug("CSRF 校验失败：xsrfCookieCount={} cookiePresent={} headerPresent={}",
            cookieCount(request, XSRF_COOKIE), cookieCount(request, XSRF_COOKIE) > 0, headerToken != null);
        writeForbidden(response);
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        String cookieName = configService.runtimeCookieProperties(cookieProperties).name();
        String value = cookieValue(request, cookieName);
        return value != null && !value.isBlank();
    }

    private static boolean isLoginEndpoint(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
            && request.getRequestURI() != null
            && request.getRequestURI().endsWith("/api/v1/auth/login");
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static long cookieCount(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return 0;
        }
        return Arrays.stream(cookies).filter(cookie -> name.equals(cookie.getName())).count();
    }

    private static String headerToken(HttpServletRequest request) {
        String token = request.getHeader(XSRF_HEADER);
        return token == null || token.isBlank() ? null : token;
    }

    private static boolean matches(String cookieToken, String headerToken) {
        if (cookieToken == null || headerToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
            cookieToken.getBytes(StandardCharsets.UTF_8),
            headerToken.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasMatchingCookie(HttpServletRequest request, String name, String headerToken) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        return Arrays.stream(cookies)
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .anyMatch(cookieToken -> matches(cookieToken, headerToken));
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("urn:medkernel:error:" + ErrorCode.FORBIDDEN.code()));
        problem.setTitle(ErrorCode.FORBIDDEN.defaultMessage());
        problem.setDetail("缺少或不匹配的页面安全凭证");
        problem.setProperty("code", ErrorCode.FORBIDDEN.code());
        problem.setProperty("errorClass", ErrorCode.FORBIDDEN.errorClass().name());
        problem.setProperty("retryable", ErrorCode.FORBIDDEN.retryable());
        problem.setProperty("traceId", RequestContext.snapshot().traceId());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
