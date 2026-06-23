package com.medkernel.engine.sandbox;

import java.time.Instant;

/** 当前认证医院沙盘运行修订的动态就绪状态。 */
public record SandboxRuntimeStatusResponse(
    boolean ready,
    String reasonCode,
    String reason,
    String targetOrgUnitId,
    String runtimeReleaseId,
    Long runtimeRevisionNo,
    String platformBaselineReleaseId,
    String manifestSha256,
    SandboxResolutionSource resolutionSource,
    int assetCount,
    Instant resolvedAt,
    boolean externalSideEffects
) {
    static SandboxRuntimeStatusResponse ready(SandboxRuntimeBaseline baseline) {
        return new SandboxRuntimeStatusResponse(
            true,
            null,
            null,
            baseline.targetOrgUnitId(),
            baseline.runtimeReleaseId(),
            baseline.runtimeRevisionNo(),
            baseline.platformBaselineReleaseId(),
            baseline.manifestSha256(),
            baseline.resolutionSource(),
            baseline.runtimeContent().items().size(),
            baseline.resolvedAt(),
            false);
    }

    static SandboxRuntimeStatusResponse notReady(
            String targetOrgUnitId,
            String reasonCode,
            String reason) {
        return new SandboxRuntimeStatusResponse(
            false, reasonCode, reason, targetOrgUnitId, null, null, null, null,
            null, 0, null, false);
    }
}
