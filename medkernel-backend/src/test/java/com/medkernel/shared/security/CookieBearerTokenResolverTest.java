package com.medkernel.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver =
        new CookieBearerTokenResolver(new AuthCookieProperties("mk_access", false, "Strict", "/medkernel", 28800));

    @Test
    void resolvesTokenFromCookie() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("mk_access", "jwt-from-cookie"));
        assertThat(resolver.resolve(req)).isEqualTo("jwt-from-cookie");
    }

    @Test
    void fallsBackToAuthorizationHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer jwt-from-header");
        assertThat(resolver.resolve(req)).isEqualTo("jwt-from-header");
    }

    @Test
    void nullCookiesFallsBackToAuthorizationHeader() {
        // getCookies() 为 null（未调用 setCookies），有 Authorization: Bearer 头 → 返回 header token
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer jwt-from-header-no-cookies");
        assertThat(resolver.resolve(req)).isEqualTo("jwt-from-header-no-cookies");
    }

    @Test
    void emptyCookieValueReturnsNull() {
        // cookie 值为空字符串，无 Authorization 头 → resolve 返回 null（空值不误判）
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("mk_access", ""));
        assertThat(resolver.resolve(req)).isNull();
    }

    @Test
    void ignoresStaleCookieOnPreAuthenticationEndpoint() {
        MockHttpServletRequest req = new MockHttpServletRequest(
            "GET", "/medkernel/api/v1/auth/login-tenants");
        req.setContextPath("/medkernel");
        req.setCookies(new Cookie("mk_access", "stale-jwt"));

        assertThat(resolver.resolve(req)).isNull();
    }

    @Test
    void protectedEndpointStillResolvesCookie() {
        MockHttpServletRequest req = new MockHttpServletRequest(
            "GET", "/medkernel/api/v1/system/runtime");
        req.setContextPath("/medkernel");
        req.setCookies(new Cookie("mk_access", "jwt-from-cookie"));

        assertThat(resolver.resolve(req)).isEqualTo("jwt-from-cookie");
    }
}
