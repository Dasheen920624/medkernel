package com.medkernel.engine.security;

import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 内置超级管理员不可变约束。
 */
@Component
public class SystemSuperAdminGuard {

    private final UserRoleAssignmentRepository roleAssignments;

    public SystemSuperAdminGuard(UserRoleAssignmentRepository roleAssignments) {
        this.roleAssignments = roleAssignments;
    }

    public static boolean isSystemSuperAdminRole(String roleCode) {
        return RoleCode.fromCode(roleCode)
            .map(RoleCode::systemSuperAdmin)
            .orElse(false);
    }

    public static void assertTenantManagedRole(String roleCode) {
        if (isSystemSuperAdminRole(roleCode)) {
            throw immutable();
        }
    }

    public static void assertAssignmentMutable(UserRoleAssignment assignment) {
        if (assignment != null && isSystemSuperAdminRole(assignment.roleCode())) {
            throw immutable();
        }
    }

    public void assertCredentialMutableByTenantManagement(String tenantId, String userId) {
        if (roleAssignments.existsByTenantIdAndUserIdAndRoleCodeAndActiveFlag(
                tenantId, userId, RoleCode.SYSTEM_SUPERADMIN.code(), "Y")) {
            throw immutable();
        }
    }

    public static ApiException immutable() {
        return new ApiException(ErrorCode.SUPERADMIN_IMMUTABLE);
    }
}
