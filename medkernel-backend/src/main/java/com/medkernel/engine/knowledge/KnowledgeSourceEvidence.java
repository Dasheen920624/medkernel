package com.medkernel.engine.knowledge;

import java.time.Instant;

/**
 * 面向临床展示 / 来源追溯消费的来源证据视图。
 *
 * <p>本视图只组合关系库已有权威来源链，不生成新医学事实。
 */
public record KnowledgeSourceEvidence(
    Long assetVersionId,
    Long citationId,
    Long sourceFragmentId,
    Long sourceDocumentId,
    Long sourceVersionId,
    String sourceCode,
    String sourceTitle,
    SourceType sourceType,
    SourceAuthorityLevel authorityLevel,
    String authorityLabel,
    String authorityBasis,
    String sourceVersionNo,
    String sourceVersionHash,
    String anchorPath,
    String anchorLabel,
    String textExcerpt,
    String fragmentHash,
    Integer startOffset,
    Integer endOffset,
    GradeEvidenceQuality gradeQuality,
    GradeRecommendationStrength gradeStrength,
    Instant publishedAt,
    CitationRelation relation,
    Integer weight,
    String organizationScope,
    String applicableScope,
    KnowledgeSourceEvidenceRole displayRole,
    boolean recommendedByDefault,
    boolean supplementary,
    String displayLabel,
    String rankingReason,
    String conflictArbitration
) {
}
