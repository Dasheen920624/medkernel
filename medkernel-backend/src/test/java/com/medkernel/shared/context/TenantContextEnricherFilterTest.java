package com.medkernel.shared.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextEnricherFilterTest {

    private final TenantContextEnricherFilter filter = new TenantContextEnricherFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();
    }

    @Test
    void enrichesRequestContextFromJwtAuthentication() throws ServletException, IOException {
        Jwt jwt = new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of(
                "sub", "u-1",
                "tenant_id", "t-1",
                "hospital_id", "h-1",
                "department_id", "d-1",
                "roles", List.of("clinical-decision-user")
            )
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        // Pretend TraceIdFilter has run upstream
        RequestContext.restore(new RequestContext.Snapshot("trace-x", OrgScope.empty(), null));

        AtomicReference<OrgScope> seen = new AtomicReference<>();
        AtomicReference<String> seenUser = new AtomicReference<>();
        AtomicReference<String> seenTrace = new AtomicReference<>();

        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                seen.set(RequestContext.currentOrgScope());
                seenUser.set(RequestContext.currentUserId().orElse(null));
                seenTrace.set(RequestContext.currentTraceId());
            }
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), chain);

        assertThat(seen.get().tenantId()).isEqualTo("t-1");
        assertThat(seen.get().hospitalId()).isEqualTo("h-1");
        assertThat(seen.get().departmentId()).isEqualTo("d-1");
        assertThat(seenUser.get()).isEqualTo("u-1");
        assertThat(seenTrace.get()).as("traceId 由 TraceIdFilter 建立，本过滤器只升级 org/user，traceId 不变")
            .isEqualTo("trace-x");

        // 退出后 OrgScope 应已被回退（防止后续业务代码越权使用）
        assertThat(RequestContext.currentOrgScope().tenantId()).isNull();
        assertThat(RequestContext.currentTraceId()).isEqualTo("trace-x");
    }

    @Test
    void passesThroughWhenNoAuthentication() throws ServletException, IOException {
        RequestContext.restore(new RequestContext.Snapshot("trace-pub", OrgScope.empty(), null));

        AtomicReference<OrgScope> seen = new AtomicReference<>();
        FilterChain chain = (req, resp) -> seen.set(RequestContext.currentOrgScope());
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/system/ping"),
            new MockHttpServletResponse(), chain);

        assertThat(seen.get().hasTenant())
            .as("白名单 / 匿名端点不应注入组织上下文")
            .isFalse();
    }
}
