package com.medkernel.engine.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.config.PrivilegedConfigChangeGuard;

/**
 * 基于 Spring Security 已认证权限的特权配置变更守卫。
 */
@Component
public class SpringSecurityPrivilegedConfigChangeGuard implements PrivilegedConfigChangeGuard {

    @Override
    public void assertSystemSuperAdminAllowed(String resourceType, String resourceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null
            && authentication.isAuthenticated()
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> RoleCode.SYSTEM_SUPERADMIN.authority().equals(authority.getAuthority()));
        if (!allowed) {
            throw ApiException.forbidden("当前账号缺少特权配置变更权限");
        }
    }
}
