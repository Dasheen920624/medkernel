package com.medkernel.engine.pkg;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 试点首发配置包模板。
 *
 * <p>模板只保存确定性的资产引用，不在代码里硬编码医学资产；平台模板可被租户复用，
 * 租户模板优先覆盖平台模板。
 */
@Table("mk_pkg_pilot_package_template")
public record PilotPackageTemplate(
    @Id @Column("template_id") String templateId,
    @Column("tenant_id") String tenantId,
    @Column("template_code") String templateCode,
    String name,
    String description,
    @Column("package_code_prefix") String packageCodePrefix,
    @Column("default_package_version") String defaultPackageVersion,
    PilotPackageTemplateStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
