package com.medkernel.engine.sandbox;

import java.util.List;

/**
 * 沙盘运行结果，汇总真实引擎路径、业务标识与嵌入启动信息。
 */
public record SandboxRunResponse(
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
    String result
) {

    public SandboxRunResponse {
        steps = steps == null ? List.of() : List.copyOf(steps);
        embedModes = embedModes == null ? List.of() : List.copyOf(embedModes);
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
            scenarioId, traceId, steps, snapshotId, triggerId, cardCount, embedToken, embedUrl,
            null, null, null, List.of(), result);
    }
}
