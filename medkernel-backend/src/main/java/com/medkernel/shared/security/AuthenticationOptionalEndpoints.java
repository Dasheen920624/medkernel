package com.medkernel.shared.security;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 登录前公开端点不读取 Bearer/Cookie，避免过期会话阻断登录自恢复。
 */
final class AuthenticationOptionalEndpoints {

    private static final Set<String> PATHS = Set.of(
        "/api/v1/bootstrap/init-token",
        "/api/v1/bootstrap/password",
        "/api/v1/auth/login",
        "/api/v1/auth/password-reset",
        "/api/v1/auth/login-tenants",
        "/api/v1/auth/delegated/status",
        "/api/v1/auth/delegated/callback"
    );

    private AuthenticationOptionalEndpoints() {
    }

    static boolean shouldIgnoreBearer(HttpServletRequest request) {
        return PATHS.contains(applicationPath(request));
    }

    private static String applicationPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        if (servletPath != null && !servletPath.isBlank()) {
            return pathInfo == null ? servletPath : servletPath + pathInfo;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri != null && contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
