package com.medkernel.engine.sandbox.replay;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 导入沙盘历史原样重放清单的请求。 */
public record SandboxReplayImportRequest(
    @NotBlank @Size(max = 64) String replayCaseId,
    @NotBlank @Size(max = 71) String sourceTenantRef,
    @NotBlank @Size(max = 71) String sourceEventRef,
    @NotBlank @Size(max = 71) String sourceTraceRef,
    @NotBlank @Size(max = 71) String sourceContextRef,
    @NotNull JsonNode contextSnapshot,
    @NotBlank @Size(min = 64, max = 64) String contextSnapshotHash,
    @NotBlank @Size(max = 128) String packageCode,
    @NotBlank @Size(max = 64) String packageVersion,
    @NotNull Instant occurredAt,
    @NotBlank @Size(min = 64, max = 64) String manifestHash,
    @NotBlank @Size(max = 64) String deidentificationProfile,
    @NotNull @Size(min = 1) List<@Valid SandboxReplayAssetImportRequest> assets
) {
    public SandboxReplayImportRequest {
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    public SandboxReplayImportRequest withManifestHash(String nextManifestHash) {
        return copy(sourceTenantRef, nextManifestHash, assets);
    }

    public SandboxReplayImportRequest withSourceTenantRef(String nextSourceTenantRef) {
        return copy(nextSourceTenantRef, manifestHash, assets);
    }

    public SandboxReplayImportRequest withAssets(List<SandboxReplayAssetImportRequest> nextAssets) {
        return copy(sourceTenantRef, manifestHash, nextAssets);
    }

    private SandboxReplayImportRequest copy(
            String nextSourceTenantRef,
            String nextManifestHash,
            List<SandboxReplayAssetImportRequest> nextAssets) {
        return new SandboxReplayImportRequest(
            replayCaseId, nextSourceTenantRef, sourceEventRef, sourceTraceRef, sourceContextRef,
            contextSnapshot, contextSnapshotHash, packageCode, packageVersion, occurredAt,
            nextManifestHash, deidentificationProfile, nextAssets);
    }
}
