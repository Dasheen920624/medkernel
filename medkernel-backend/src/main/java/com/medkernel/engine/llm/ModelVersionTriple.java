package com.medkernel.engine.llm;

/**
 * LLM-04 提示词、工具和模型版本组合。
 */
public record ModelVersionTriple(
    String promptVersion,
    String toolVersion,
    String modelVersion
) {

    public static ModelVersionTriple baseline() {
        return new ModelVersionTriple("baseline", "gateway-default", "B0-Deterministic-Baseline");
    }
}
