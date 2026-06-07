package com.medkernel.engine.security;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 租户用户主体，是凭证、角色和外部身份绑定共同引用的唯一用户目录。
 */
@Table("tenant_user")
public record TenantUser(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("display_name") String displayName,
    @Column("status") String status,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public boolean active() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
