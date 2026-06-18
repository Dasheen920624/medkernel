package com.medkernel.engine.llm.eval;

import java.time.Instant;

/**
 * AI 质量评测趋势点：一条模型版本评测运行在时间线上的质量摘要。
 */
public record AiQualityTrendPoint(
    Long runId,
    Instant createdAt,
    String providerCode,
    String modelVersion,
    String promptVersion,
    String toolVersion,
    String status,
    Double qualityScore,
    Double terminologyScore,
    boolean hallucinationDetected
) {
}
