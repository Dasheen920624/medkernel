package com.medkernel.engine.llm.eval;

import java.util.List;

/**
 * AI 质量评测趋势响应：按能力码和模型版本返回最近运行质量点。
 */
public record AiQualityTrendResponse(
    String capabilityCode,
    String modelVersion,
    List<AiQualityTrendPoint> points
) {
    public AiQualityTrendResponse {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
