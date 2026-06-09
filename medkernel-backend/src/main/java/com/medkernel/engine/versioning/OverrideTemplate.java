package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 可复用组织覆盖模板。
 */
@Table("mk_version_override_template")
public record OverrideTemplate(
    @Id Long id,
    @Column("template_id") String templateId,
    @Column("tenant_id") String tenantId,
    @Column("template_name") String templateName,
    String description,
    @Column("applicable_scope") String applicableScope,
    OverrideTemplateStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
