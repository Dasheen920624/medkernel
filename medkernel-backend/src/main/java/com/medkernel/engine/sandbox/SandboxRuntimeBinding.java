package com.medkernel.engine.sandbox;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 演练机构明确激活的沙盘运行配置包绑定。 */
@Table("mk_sandbox_runtime_binding")
public record SandboxRuntimeBinding(
    @Id Long id,
    @Column("binding_id") String bindingId,
    @Column("tenant_id") String tenantId,
    @Column("target_org_unit_id") String targetOrgUnitId,
    @Column("package_owner_tenant_id") String packageOwnerTenantId,
    @Column("package_id") String packageId,
    @Column("package_code") String packageCode,
    @Column("package_version") String packageVersion,
    SandboxRuntimeBindingStatus status,
    @Column("active_scope_key") String activeScopeKey,
    @Column("activated_at") Instant activatedAt,
    @Column("activated_by") String activatedBy,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
