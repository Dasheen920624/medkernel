package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 知识资产版本：实际承载临床决策依据的内容载体。
 *
 * <p>关键不变量：同一知识身份、组织适用域、临床适用域下，同时刻
 * {@link KnowledgeVersionStatus#ACTIVE} ≤ 1。Service 事务先做原子替换，DB 唯一约束兜底。
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
    @Column("authority_level") SourceAuthorityLevel authorityLevel,
    @Column("grade_quality") GradeEvidenceQuality gradeQuality,
    @Column("grade_strength") GradeRecommendationStrength gradeStrength,
    @Column("conflict_arbitration") String conflictArbitration,
    @Column("organization_scope") String organizationScope,
    @Column("applicable_scope") String applicableScope,
    @Column("active_scope_key") String activeScopeKey,
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

    public static final String DEFAULT_APPLICABLE_SCOPE = "ALL";

    public boolean isAuthoritative() {
        return status != null && status.isAuthoritative();
    }

    public boolean isHighRisk() {
        return riskLevel == KnowledgeRiskLevel.HIGH;
    }

    public String effectiveOrganizationScope() {
        return normalize(organizationScope, "tenant:" + tenantId);
    }

    public String effectiveApplicableScope() {
        return normalize(applicableScope, DEFAULT_APPLICABLE_SCOPE);
    }

    public String activeScopeKeyForActiveStatus() {
        return activeScopeKey(identityId, effectiveOrganizationScope(), effectiveApplicableScope());
    }

    public String inactiveScopeKey() {
        if (id != null) {
            return "version:" + id;
        }
        return "version-pending:" + identityId + ":" + normalize(versionNo, "unknown");
    }

    public String scopeKeyForStatus(KnowledgeVersionStatus nextStatus) {
        return nextStatus == KnowledgeVersionStatus.ACTIVE ? activeScopeKeyForActiveStatus() : inactiveScopeKey();
    }

    public static String activeScopeKey(Long identityId, String organizationScope, String applicableScope) {
        return identityId + "|" + normalize(organizationScope, "tenant:unknown")
            + "|" + normalize(applicableScope, DEFAULT_APPLICABLE_SCOPE);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
