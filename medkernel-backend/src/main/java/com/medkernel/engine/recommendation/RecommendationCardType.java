package com.medkernel.engine.recommendation;

/**
 * 推荐卡业务类型：MEDICATION 用药 / EXAM 检查 / LAB 检验 / PATHWAY 路径 / RISK 风险 /
 * KNOWLEDGE 知识 / QUALITY 质控 / NURSING 护理 / FOLLOWUP 随访 / DIAGNOSIS 诊断辅助（鉴别诊断）。
 */
public enum RecommendationCardType {
    MEDICATION,
    EXAM,
    LAB,
    PATHWAY,
    RISK,
    KNOWLEDGE,
    QUALITY,
    NURSING,
    FOLLOWUP,
    /** 诊断辅助：运行时鉴别诊断候选卡（确定性命中，需医师确认，非自动诊断）。 */
    DIAGNOSIS
}
