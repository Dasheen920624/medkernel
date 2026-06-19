package com.medkernel.engine.sandbox;

import java.time.Instant;

import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.sandbox.replay.SandboxReplayResolvedCase;

/** 单次沙盘运行开始时冻结的解析结果；执行过程中不得再次解析。 */
public record SandboxRuntimeBaseline(
    String baselineId,
    SandboxRunMode mode,
    String tenantId,
    String targetOrgUnitId,
    String bindingId,
    String packageOwnerTenantId,
    String packageId,
    String packageCode,
    String packageVersion,
    SandboxResolutionSource resolutionSource,
    Instant resolvedAt,
    EffectiveKnowledgePackageResponse effectivePackage,
    String replayCaseId,
    SandboxReplayResolvedCase historicalReplay
) {
}
