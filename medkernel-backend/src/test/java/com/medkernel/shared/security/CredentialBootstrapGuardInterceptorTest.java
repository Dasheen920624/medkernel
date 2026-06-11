package com.medkernel.shared.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialBootstrapGuardInterceptorTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();
    }

    @Test
    void allowsChangePasswordWhenApplicationContextPathPrefixesRequestUri() {
        CredentialBootstrapGuardInterceptor interceptor =
            new CredentialBootstrapGuardInterceptor((tenantId, userId) -> true);
        authenticateMustChangeUser();

        assertThatCode(() -> interceptor.preHandle(
            request("/medkernel", "/api/v1/auth/change-password"),
            new MockHttpServletResponse(),
            new Object()))
            .doesNotThrowAnyException();
    }

    @Test
    void stillBlocksBusinessApiWhenMustChangePasswordWithApplicationContextPath() {
        CredentialBootstrapGuardInterceptor interceptor =
            new CredentialBootstrapGuardInterceptor((tenantId, userId) -> true);
        authenticateMustChangeUser();

        assertThatThrownBy(() -> interceptor.preHandle(
            request("/medkernel", "/api/v1/security/menu-permissions/visible"),
            new MockHttpServletResponse(),
            new Object()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).errorCode()).isEqualTo(ErrorCode.ENG_AUTH_015));
    }

    private static void authenticateMustChangeUser() {
        RequestContext.restore(new RequestContext.Snapshot("trace-auth-015", OrgScope.tenant("t-1"), "doctor-1"));
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("doctor-1")
            .claim("tenant_id", "t-1")
            .claim("roles", List.of("clinical-decision-user"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .claims(claims -> claims.putAll(Map.of("user_id", "doctor-1")))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
            jwt,
            List.of(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))));
    }

    private static MockHttpServletRequest request(String contextPath, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", contextPath + servletPath);
        request.setContextPath(contextPath);
        request.setServletPath(servletPath);
        request.setRequestURI(contextPath + servletPath);
        return request;
    }
}
