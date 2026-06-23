package com.medkernel.engine.sandbox;

import java.time.Instant;

import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.sandbox.replay.SandboxReplayResolvedCase;

/** 单次沙盘运行开始时冻结的运行修订或历史重放清单；执行过程中不得再次解析。 */
public record SandboxRuntimeBaseline(
    String baselineId,
    SandboxRunMode mode,
    String tenantId,
    String targetOrgUnitId,
    String runtimeReleaseId,
    Long runtimeRevisionNo,
    String platformBaselineReleaseId,
    String manifestSha256,
    SandboxResolutionSource resolutionSource,
    Instant resolvedAt,
    ClinicalRuntimeReleaseContent runtimeContent,
    String replayCaseId,
    SandboxReplayResolvedCase historicalReplay
) {
    /** 当前运行修订返回真实 ID；历史重放只返回不可逆来源引用。 */
    public String runtimeReleaseRef() {
        if (runtimeReleaseId != null) {
            return runtimeReleaseId;
        }
        return historicalReplay == null
            ? null
            : historicalReplay.replayCase().sourceRuntimeReleaseRef();
    }
}
