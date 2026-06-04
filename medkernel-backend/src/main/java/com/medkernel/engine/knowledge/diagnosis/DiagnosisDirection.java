package com.medkernel.engine.knowledge.diagnosis;

/**
 * 诊断标准方向：发现项对某诊断的作用。SUPPORTING 支持 / REFUTING 反对 / REQUIRED 必需 / EXCLUSION 排除。
 */
public enum DiagnosisDirection {
    /** 支持：命中则增加该诊断证据。 */
    SUPPORTING,
    /** 反对：命中则降低该诊断可能（不直接排除）。 */
    REFUTING,
    /** 必需：缺失则该诊断不能成立（置信降级）。 */
    REQUIRED,
    /** 排除：命中则直接排除该诊断。 */
    EXCLUSION
}
