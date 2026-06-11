package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 客户机构从平台权威知识按需派生的血缘记录。
 */
@Table("mk_knowledge_customization")
public record KnowledgeCustomization(
    @Id @Column("customization_id") String customizationId,
    @Column("tenant_id") String tenantId,
    @Column("platform_identity_id") Long platformIdentityId,
    @Column("platform_version_id") Long platformVersionId,
    @Column("platform_version_no") String platformVersionNo,
    @Column("local_identity_id") Long localIdentityId,
    @Column("local_version_id") Long localVersionId,
    @Column("target_org_unit_id") String targetOrgUnitId,
    @Column("target_org_path") String targetOrgPath,
    @Column("applicable_scope") String applicableScope,
    @Column("source_type") KnowledgeSourceType sourceType,
    @Column("status") KnowledgeCustomizationStatus status,
    @Column("reason") String reason,
    @Column("override_id") String overrideId,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) implements Persistable<String> {

    @Override
    public String getId() {
        return customizationId;
    }

    @Override
    public boolean isNew() {
        return version != null && version == 1L && createdAt != null && createdAt.equals(updatedAt);
    }
}
