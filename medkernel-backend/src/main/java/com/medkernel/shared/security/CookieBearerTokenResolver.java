package com.medkernel.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

import com.medkernel.shared.config.SystemConfigService;

/**
 * 优先从 mk_access cookie 取 JWT；无则回退标准 Authorization: Bearer（兼容 embed / API 客户端）。
 */
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final AuthCookieProperties cookieProps;
    private final SystemConfigService configService;
    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Autowired
    public CookieBearerTokenResolver(AuthCookieProperties cookieProps, SystemConfigService configService) {
        this.cookieProps = cookieProps;
        this.configService = configService;
    }

    CookieBearerTokenResolver(AuthCookieProperties cookieProps) {
        this.cookieProps = cookieProps;
        this.configService = null;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (AuthenticationOptionalEndpoints.shouldIgnoreBearer(request)) {
            return null;
        }
        if (request.getCookies() != null) {
            String cookieName = configService == null
                ? cookieProps.name()
                : configService.runtimeCookieProperties(cookieProps).name();
            for (Cookie c : request.getCookies()) {
                if (cookieName.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        return headerResolver.resolve(request);
    }
}
