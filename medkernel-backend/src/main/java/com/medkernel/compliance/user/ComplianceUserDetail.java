package com.medkernel.compliance.user;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.security.EffectivePermissionProfile;

/**
 * 用户管理详情，包含当前组织上下文中的有效权限证据。
 */
public record ComplianceUserDetail(
    String userId,
    String displayName,
    String username,
    boolean credentialManaged,
    String status,
    boolean mustChangePwd,
    List<ComplianceUserRole> roles,
    List<EffectivePermissionProfile.PermissionView> effectivePermissions,
    Instant createdAt,
    Instant updatedAt
) {
}
