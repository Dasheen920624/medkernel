package com.medkernel.engine.sandbox.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 历史重放资产的精确版本与内容快照。 */
public record SandboxReplayAssetImportRequest(
    @NotNull VersionedAssetType assetType,
    @NotBlank @Size(max = 128) String assetIdentity,
    @NotBlank @Size(max = 128) String versionId,
    @NotBlank @Size(max = 64) String assetVersion,
    @NotNull SourceTier sourceTier,
    @NotBlank @Size(max = 71) String sourceOrgRef,
    @NotNull JsonNode content,
    @NotBlank @Size(min = 64, max = 64) String contentHash,
    @NotNull AssetVersionStatus historicalStatus
) {
    public SandboxReplayAssetImportRequest withContentHash(String nextContentHash) {
        return new SandboxReplayAssetImportRequest(
            assetType, assetIdentity, versionId, assetVersion, sourceTier, sourceOrgRef,
            content, nextContentHash, historicalStatus);
    }
}
