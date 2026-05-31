package com.medkernel.engine.security;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 应急权限授予记录。
 *
 * <p>break-glass 授权只能授予 {@code env.emergency}，并且必须有过期时间；到期后权限引擎自动视为失效。
 */
@Table("emergency_permission_grant")
public record EmergencyPermissionGrant(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("permission_code") String permissionCode,
    @Column("reason") String reason,
    @Column("granted_by") String grantedBy,
    @Column("granted_at") Instant grantedAt,
    @Column("expires_at") Instant expiresAt,
    @Column("revoked_at") Instant revokedAt,
    @Column("revoked_by") String revokedBy,
    @Column("active_flag") String activeFlag,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    public boolean activeAt(Instant now) {
        return PermissionCode.ENV_EMERGENCY.code().equals(permissionCode)
            && "Y".equalsIgnoreCase(activeFlag)
            && revokedAt == null
            && expiresAt != null
            && now != null
            && expiresAt.isAfter(now);
    }
}
