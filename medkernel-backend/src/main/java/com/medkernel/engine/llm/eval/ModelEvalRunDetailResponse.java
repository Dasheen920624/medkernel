package com.medkernel.engine.llm.eval;

import java.util.List;

/**
 * 医学回归评测运行详情及当前交付内容有效性。
 */
public record ModelEvalRunDetailResponse(
    ModelEvalRunSummaryResponse run,
    List<ModelEvalCaseEvidenceResponse> cases,
    boolean evidenceComplete,
    boolean baselineCurrent,
    boolean releaseCurrent
) {
    public ModelEvalRunDetailResponse {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
