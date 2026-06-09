package com.medkernel.shared.security;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.JwtClaimsResolver;
import com.medkernel.shared.context.RequestContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 首登强制改密拦截器：必须改密的账号只能访问改密、会话与当前身份状态接口。
 */
@Component
public class CredentialBootstrapGuardInterceptor implements HandlerInterceptor {

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
        "/api/v1/auth/change-password",
        "/api/v1/auth/logout",
        "/api/v1/auth/session",
        "/api/v1/security/me",
        "/api/v1/bootstrap/status",
        "/api/v1/bootstrap/init-token",
        "/api/v1/bootstrap/password",
        "/api/v1/auth/login",
        "/api/v1/auth/login-tenants",
        "/api/v1/auth/delegated/status",
        "/api/v1/auth/delegated/callback",
        "/actuator/",
        "/v3/api-docs",
        "/swagger-ui.html",
        "/swagger-ui"
    );

    private final CredentialBootstrapStatusProvider statusProvider;

    public CredentialBootstrapGuardInterceptor(CredentialBootstrapStatusProvider statusProvider) {
        this.statusProvider = statusProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isAllowed(applicationPath(request))) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }

        Jwt jwt = resolveJwt(authentication);
        String userId = RequestContext.currentUserId().orElse(jwt == null
            ? authentication.getName()
            : JwtClaimsResolver.resolveUserId(jwt));
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if ((tenantId == null || tenantId.isBlank()) && jwt != null) {
            tenantId = JwtClaimsResolver.resolveOrgScope(jwt).tenantId();
        }
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            return true;
        }
        if (statusProvider.mustChangePassword(tenantId, userId)) {
            throw new ApiException(ErrorCode.ENG_AUTH_015, "首次登录或密码重置后必须先完成密码修改");
        }
        return true;
    }

    private static boolean isAllowed(String uri) {
        if (uri == null || uri.isBlank()) {
            return true;
        }
        return ALLOWED_PREFIXES.stream().anyMatch(prefix -> {
            if (prefix.endsWith("/")) {
                return uri.startsWith(prefix);
            }
            return uri.equals(prefix) || uri.startsWith(prefix + "/");
        });
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

    private static Jwt resolveJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }
}
