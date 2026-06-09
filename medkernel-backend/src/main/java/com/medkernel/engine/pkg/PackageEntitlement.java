package com.medkernel.engine.pkg;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 租户对受限平台知识包的授权事实。
 */
@Table("mk_pkg_package_entitlement")
public record PackageEntitlement(
    @Id Long id,
    @Column("entitlement_id") String entitlementId,
    @Column("tenant_id") String tenantId,
    @Column("platform_tenant_id") String platformTenantId,
    @Column("platform_package_id") String platformPackageId,
    @Column("package_identity") String packageIdentity,
    PackageEntitlementStatus status,
    @Column("granted_at") Instant grantedAt,
    @Column("expires_at") Instant expiresAt,
    String reason,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
