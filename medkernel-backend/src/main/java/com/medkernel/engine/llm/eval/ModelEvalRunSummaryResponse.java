package com.medkernel.engine.llm.eval;

import java.time.Instant;

/**
 * 医学回归评测运行安全摘要，不暴露租户字段或模型凭据。
 */
public record ModelEvalRunSummaryResponse(
    Long runId,
    String providerCode,
    String modelVersion,
    String capabilityCode,
    String promptVersion,
    String toolVersion,
    String releaseFingerprint,
    int totalCases,
    int passedCases,
    int failedCases,
    boolean fakeCitationDetected,
    boolean redLineBreach,
    boolean hallucinationDetected,
    String status,
    Instant createdAt,
    String createdBy
) {
}
