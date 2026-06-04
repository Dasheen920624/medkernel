package com.medkernel.engine.knowledge.diagnosis;

/**
 * 诊断置信分级（确定性等级，<b>非百分比概率</b>，守 NMPA 三类器械边界）。
 *
 * <p>STRONG 强 / MODERATE 中 / WEAK 弱 / EXCLUDE 排除。无候选 ≠ 排除（空态由运行时单独表达）。
 */
public enum DiagnosisConfidence {
    /** 强：必需项满足且主要标准充足。 */
    STRONG,
    /** 中：必需项满足但主要标准不足。 */
    MODERATE,
    /** 弱：必需项缺失或证据不足。 */
    WEAK,
    /** 排除：命中排除项。 */
    EXCLUDE
}
