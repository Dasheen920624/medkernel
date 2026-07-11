package com.medkernel.engine.sandbox;

import java.util.List;

import com.medkernel.engine.sandbox.compare.SandboxComparisonResponse;
import com.medkernel.engine.sandbox.replay.SandboxReplayRuleResult;

/**
 * 沙盘运行结果，汇总真实医疗智能链路、业务标识与嵌入启动信息。
 */
public record SandboxRunResponse(
    String scenarioId,
    String traceId,
    String runId,
    String baselineId,
    SandboxRunMode mode,
    String runtimeReleaseRef,
    Long runtimeRevisionNo,
    SandboxResolutionSource resolutionSource,
    boolean externalSideEffects,
    List<SandboxStepTrace> steps,
    String snapshotId,
    String triggerId,
    int cardCount,
    String embedToken,
    String embedUrl,
    String hookInstance,
    String patientPathwayId,
    String followupPlanId,
    String evaluationRunId,
    List<String> embedModes,
    String result,
    String replayCaseId,
    List<SandboxReplayRuleResult> replayRuleResults,
    SandboxComparisonResponse comparison
) {

    public SandboxRunResponse {
        steps = steps == null ? List.of() : List.copyOf(steps);
        embedModes = embedModes == null ? List.of() : List.copyOf(embedModes);
        replayRuleResults = replayRuleResults == null ? List.of() : List.copyOf(replayRuleResults);
    }

    public SandboxRunResponse(
            String scenarioId,
            String traceId,
            List<SandboxStepTrace> steps,
            String snapshotId,
            String triggerId,
            int cardCount,
            String embedToken,
            String embedUrl,
            String hookInstance,
            String patientPathwayId,
            String followupPlanId,
            String evaluationRunId,
            List<String> embedModes,
            String result) {
        this(
            scenarioId, traceId, null, null, SandboxRunMode.CURRENT, null, null, null, false,
            steps, snapshotId, triggerId, cardCount, embedToken, embedUrl, hookInstance,
            patientPathwayId, followupPlanId, evaluationRunId, embedModes, result, null, List.of(), null);
    }

    public SandboxRunResponse(
            String scenarioId,
            String traceId,
            List<SandboxStepTrace> steps,
            String snapshotId,
            String triggerId,
            int cardCount,
            String embedToken,
            String embedUrl,
            String patientPathwayId,
            String followupPlanId,
            String evaluationRunId,
            List<String> embedModes,
            String result) {
        this(
            scenarioId, traceId, null, null, SandboxRunMode.CURRENT, null, null, null, false,
            steps, snapshotId, triggerId, cardCount, embedToken, embedUrl,
            null, patientPathwayId, followupPlanId, evaluationRunId, embedModes, result, null, List.of(), null);
    }

    public SandboxRunResponse(
            String scenarioId,
            String traceId,
            List<SandboxStepTrace> steps,
            String snapshotId,
            String triggerId,
            int cardCount,
            String embedToken,
            String embedUrl,
            String result) {
        this(
            scenarioId, traceId, null, null, SandboxRunMode.CURRENT, null, null, null, false,
            steps, snapshotId, triggerId, cardCount, embedToken, embedUrl,
            null, null, null, null, List.of(), result, null, List.of(), null);
    }

    /** 将一次执行结果绑定到已经冻结的运行基线。 */
    public SandboxRunResponse withRuntime(String effectiveRunId, SandboxRuntimeBaseline baseline) {
        return new SandboxRunResponse(
            scenarioId, traceId, effectiveRunId, baseline.baselineId(), baseline.mode(),
            baseline.runtimeReleaseRef(), baseline.runtimeRevisionNo(),
            baseline.resolutionSource(), false, steps, snapshotId,
            triggerId, cardCount, embedToken, embedUrl, hookInstance, patientPathwayId,
            followupPlanId, evaluationRunId, embedModes, result, baseline.replayCaseId(),
            replayRuleResults, comparison);
    }
}
