package com.medkernel.engine.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前请求固定权限包门禁。
 *
 * <p>业务代码只声明所需权限，不感知职责角色名称。权限来自已验签认证中的标准职责角色，
 * 再按 {@link DefaultPermissionPolicy} 的固定权限包解析；旧职责 authority 不参与授权。
 */
public final class AuthenticatedPermissionGuard {

    private AuthenticatedPermissionGuard() {
    }

    public static boolean has(PermissionCode permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || permission == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(authority -> RoleCode.fromAuthority(authority.getAuthority()))
            .flatMap(java.util.Optional::stream)
            .anyMatch(role -> DefaultPermissionPolicy.has(role, permission));
    }
}
