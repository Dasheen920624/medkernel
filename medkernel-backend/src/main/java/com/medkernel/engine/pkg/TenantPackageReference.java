package com.medkernel.engine.pkg;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 租户对平台知识包的引用授权。
 */
@Table("mk_pkg_tenant_package_reference")
public record TenantPackageReference(
    @Id Long id,
    @Column("reference_id") String referenceId,
    @Column("tenant_id") String tenantId,
    @Column("platform_tenant_id") String platformTenantId,
    @Column("platform_package_id") String platformPackageId,
    @Column("package_code") String packageCode,
    @Column("package_version") String packageVersion,
    @Column("target_org_unit_id") String targetOrgUnitId,
    @Column("source_template_code") String sourceTemplateCode,
    @Column("status") TenantPackageReferenceStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
