package com.medkernel.engine.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前请求已认证角色门禁。
 *
 * <p>高风险业务动作只能信任 Spring Security 验签后生成的 authority，
 * 禁止使用请求体中的角色字段作为授权依据。
 */
public final class AuthenticatedRoleGuard {

    private AuthenticatedRoleGuard() {
    }

    public static boolean has(RoleCode requiredRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || requiredRole == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> RoleCode.fromAuthority(authority.getAuthority())
                .filter(requiredRole::equals)
                .isPresent());
    }
}
