package com.medkernel.engine.versioning;

import java.util.List;

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
    String impactDigest,
    String reviewConclusion,
    List<String> roleCodes,
    String actor,
    String traceId,
    VersionElectronicSignature electronicSignature,
    VersionPublishQualityGate qualityGate
) {
    public VersionReleaseCommand {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public VersionReleaseCommand(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String versionId,
            String targetOrgPath,
            String applicableScope,
            VersionReleaseScopeType scopeType,
            String scopeValue,
            String impactDigest,
            String reviewConclusion,
            List<String> roleCodes,
            String actor,
            String traceId) {
        this(
            tenantId,
            assetType,
            assetIdentity,
            versionId,
            targetOrgPath,
            applicableScope,
            scopeType,
            scopeValue,
            impactDigest,
            reviewConclusion,
            roleCodes,
            actor,
            traceId,
            null,
            null
        );
    }
}
