package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 知识身份（跨版本的稳定主题，比如"药品说明书" / "专科诊疗指南"）。
 *
 * <p>{@code current_version_id} 由 Service 层在激活/替换/撤回时维护，
 * 表示无适用域参数旧入口的默认当前权威版本；适用域内权威性由版本表 scope 字段判定。
 * 客户端不应直接修改它。
 */
@Table("knowledge_identity")
public record KnowledgeIdentity(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("identity_code") String identityCode,
    @Column("domain") KnowledgeDomain domain,
    @Column("subject") String subject,
    @Column("specialty_id") String specialtyId,
    @Column("description") String description,
    @Column("status") KnowledgeIdentityStatus status,
    @Column("current_version_id") Long currentVersionId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    public boolean isActive() {
        return status == KnowledgeIdentityStatus.ACTIVE;
    }

    public boolean hasCurrentVersion() {
        return currentVersionId != null;
    }
}
