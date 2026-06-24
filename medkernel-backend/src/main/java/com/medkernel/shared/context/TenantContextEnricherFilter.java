package com.medkernel.shared.context;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MedKernel v1.0 GA · GA-ENG-BASE-01 在 Spring Security 鉴权之后丰富 {@link RequestContext}。
 *
 * <p>{@code TraceIdFilter} 已经在最早阶段建立了仅含 traceId 的 RequestContext.Snapshot；
 * Spring Security 验签完 JWT 后将 {@link JwtAuthenticationToken} 放入 SecurityContextHolder；
 * 本过滤器读取它，调用 {@link JwtClaimsResolver} 解析 OrgScope + userId，
 * 并将 RequestContext 升级为带组织/用户的完整 snapshot。
 *
 * <p>未携带 JWT 的请求（匿名允许入口如 /api/v1/system/ping、/actuator/health）保持 traceId-only snapshot，
 * 是否进一步要求 tenantId 由业务层显式抛 {@code ApiException.tenantMissing()} 控制。
 *
 * <p>注册位置：由 {@code SecurityConfig} 调用 {@code addFilterAfter(this, BearerTokenAuthenticationFilter.class)}。
 */
@Component
public class TenantContextEnricherFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean enriched = enrich();
        try {
            chain.doFilter(request, response);
        } finally {
            // TraceIdFilter 的 finally 也会 clear；这里只在我们升级了 snapshot 时回退到 traceId-only，
            // 避免业务代码意外保留 OrgScope。注意：TraceIdFilter 一定会在更外层调 clear()。
            if (enriched) {
                RequestContext.Snapshot now = RequestContext.snapshot();
                RequestContext.restore(new RequestContext.Snapshot(now.traceId(), OrgScope.empty(), null));
            }
        }
    }

    private boolean enrich() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return false;
        }
        Jwt jwt = jwtAuth.getToken();
        OrgScope org = JwtClaimsResolver.resolveOrgScope(jwt);
        String userId = JwtClaimsResolver.resolveUserId(jwt);

        RequestContext.Snapshot existing = RequestContext.snapshot();
        RequestContext.restore(new RequestContext.Snapshot(existing.traceId(), org, userId));
        return true;
    }
}
