package com.medkernel.engine.sandbox;

import java.time.Instant;
import java.util.List;

/** 当前演练机构沙盘运行绑定和动态就绪状态。 */
public record SandboxRuntimeStatusResponse(
    boolean ready,
    String reasonCode,
    String reason,
    String targetOrgUnitId,
    String bindingId,
    String packageOwnerTenantId,
    String packageId,
    String packageCode,
    String packageVersion,
    SandboxResolutionSource resolutionSource,
    int assetCount,
    List<String> warnings,
    Instant resolvedAt,
    boolean externalSideEffects
) {
    public SandboxRuntimeStatusResponse {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    static SandboxRuntimeStatusResponse ready(SandboxRuntimeBaseline baseline) {
        return new SandboxRuntimeStatusResponse(
            true, null, null, baseline.targetOrgUnitId(), baseline.bindingId(),
            baseline.packageOwnerTenantId(), baseline.packageId(), baseline.packageCode(),
            baseline.packageVersion(), baseline.resolutionSource(),
            baseline.effectivePackage().items().size(), baseline.effectivePackage().warnings(),
            baseline.resolvedAt(), false);
    }

    static SandboxRuntimeStatusResponse ready(
            SandboxRuntimeBinding binding,
            SandboxResolutionSource source,
            int assetCount,
            List<String> warnings) {
        return new SandboxRuntimeStatusResponse(
            true, null, null, binding.targetOrgUnitId(), binding.bindingId(),
            binding.packageOwnerTenantId(), binding.packageId(), binding.packageCode(),
            binding.packageVersion(), source, assetCount, warnings, binding.activatedAt(), false);
    }

    static SandboxRuntimeStatusResponse notReady(
            String targetOrgUnitId,
            String reasonCode,
            String reason) {
        return new SandboxRuntimeStatusResponse(
            false, reasonCode, reason, targetOrgUnitId, null, null, null, null, null,
            null, 0, List.of(), null, false);
    }
}
