package com.medkernel.shared.audit;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审计操作者分类工具。
 *
 * <p>BASE-04 只负责识别并高亮审计事件；内置超管的创建、不可撤销和 MFA 约束
 * 由 SUPERADMIN-01 交付。这里保留明确角色令牌，避免把平台管理员误当成超管。
 */
public final class AuditActorClassifier {

    public static final String ROLE_SYSTEM_SUPERADMIN = "ROLE_SYSTEM_SUPERADMIN";
    public static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    public static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";

    private static final Set<String> SUPER_ADMIN_ROLES = Set.of(
        ROLE_SYSTEM_SUPERADMIN,
        ROLE_SUPER_ADMIN,
        ROLE_SUPERADMIN);

    private AuditActorClassifier() {
    }

    public static boolean isSuperAdminAction(String actorRoles) {
        if (actorRoles == null || actorRoles.isBlank()) {
            return false;
        }
        Set<String> roles = Arrays.stream(actorRoles.split(","))
            .map(String::trim)
            .filter(role -> !role.isBlank())
            .collect(Collectors.toSet());
        return roles.stream().anyMatch(SUPER_ADMIN_ROLES::contains);
    }

    public static Set<String> superAdminRoles() {
        return SUPER_ADMIN_ROLES;
    }
}
