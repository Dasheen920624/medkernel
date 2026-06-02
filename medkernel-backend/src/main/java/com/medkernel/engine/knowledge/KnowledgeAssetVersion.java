package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 知识资产版本：实际承载临床决策依据的内容载体。
 *
 * <p>关键不变量（由 Service 层事务保证）：同一 {@code identityId} 同时刻 {@link KnowledgeVersionStatus#ACTIVE} ≤ 1。
 *
 * <p>{@code anchors} 保留版本级来源摘要；逐条断言的精确来源以 {@link Citation} 指向
 * {@link SourceFragment} 及片段内偏移为准。
 */
@Table("knowledge_asset_version")
public record KnowledgeAssetVersion(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("identity_id") Long identityId,
    @Column("version_no") String versionNo,
    @Column("version_label") String versionLabel,
    @Column("source_document_id") Long sourceDocumentId,
    @Column("source_version_id") Long sourceVersionId,
    @Column("content_hash") String contentHash,
    @Column("anchors") String anchors,
    @Column("status") KnowledgeVersionStatus status,
    @Column("risk_level") KnowledgeRiskLevel riskLevel,
    @Column("effective_from") Instant effectiveFrom,
    @Column("effective_to") Instant effectiveTo,
    @Column("reviewed_by") String reviewedBy,
    @Column("reviewed_at") Instant reviewedAt,
    @Column("activated_at") Instant activatedAt,
    @Column("superseded_at") Instant supersededAt,
    @Column("withdrawn_at") Instant withdrawnAt,
    @Column("withdrawn_reason") String withdrawnReason,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    public boolean isAuthoritative() {
        return status != null && status.isAuthoritative();
    }

    public boolean isHighRisk() {
        return riskLevel == KnowledgeRiskLevel.HIGH;
    }
}
