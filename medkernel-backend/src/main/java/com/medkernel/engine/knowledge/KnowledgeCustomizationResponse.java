package com.medkernel.engine.knowledge;

import java.time.Instant;

/**
 * 客户可读的知识派生血缘视图。
 */
public record KnowledgeCustomizationResponse(
    String customizationId,
    KnowledgeSourceType sourceType,
    KnowledgeCustomizationStatus status,
    Long platformIdentityId,
    Long platformVersionId,
    String platformVersionNo,
    Long localIdentityId,
    Long localVersionId,
    KnowledgeRiskLevel riskLevel,
    String targetOrgUnitId,
    String targetOrganizationName,
    String targetOrgPath,
    String applicableScope,
    String reason,
    String overrideId,
    boolean platformUpdateAvailable,
    Instant updatedAt
) {
}
