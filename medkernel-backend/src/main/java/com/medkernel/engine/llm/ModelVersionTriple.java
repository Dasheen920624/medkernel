package com.medkernel.engine.llm;

/**
 * LLM-04 prompt/tool/model 版本三元组。
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
