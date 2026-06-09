package com.medkernel.engine.versioning;

/**
 * 版本发布命令。
 */
public record VersionReleaseCommand(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionId,
    String targetOrgPath,
    String applicableScope,
    VersionReleaseScopeType scopeType,
    String scopeValue,
    RolloutPolicy rolloutPolicy,
    String impactDigest,
    String reviewConclusion,
    String actor,
    String traceId,
    VersionElectronicSignature electronicSignature,
    VersionPublishQualityGate qualityGate
) {
}
