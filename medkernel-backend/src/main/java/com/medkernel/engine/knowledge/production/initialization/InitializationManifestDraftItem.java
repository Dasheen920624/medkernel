package com.medkernel.engine.knowledge.production.initialization;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.versioning.VersionedAssetType;

/** 初始化清单中一条已解析候选的规范事实。 */
public record InitializationManifestDraftItem(
    String catalogCode,
    VersionedAssetType assetType,
    String canonicalId,
    String namespace,
    String assetVersion,
    Long sourceVersionId,
    String sourceHash,
    String candidateRef,
    String candidateContentHash,
    KnowledgeRiskLevel riskLevel,
    boolean generatedByModel,
    List<String> dependencyCanonicalIds,
    String parentCanonicalId,
    String unitDimension,
    String conversionTargetCanonicalId,
    String sourcePolicy,
    String reviewPolicy,
    String testEvidenceRef,
    String ownerRole,
    String runtimeConsumers,
    String rollbackStrategy,
    InitializationChangeType changeType,
    String replacementCanonicalId,
    Instant effectiveTo
) {
    public InitializationManifestDraftItem {
        dependencyCanonicalIds = dependencyCanonicalIds == null ? List.of() : List.copyOf(dependencyCanonicalIds);
    }
}
