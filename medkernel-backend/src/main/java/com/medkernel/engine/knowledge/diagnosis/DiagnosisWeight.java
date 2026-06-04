package com.medkernel.engine.knowledge.diagnosis;

/** 诊断标准权重：主要标准 / 次要标准（用于置信分级，不输出百分比概率）。 */
public enum DiagnosisWeight {
    /** 主要标准。 */
    MAJOR,
    /** 次要标准。 */
    MINOR
}
