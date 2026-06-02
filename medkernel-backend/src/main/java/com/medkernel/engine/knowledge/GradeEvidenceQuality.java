package com.medkernel.engine.knowledge;

/**
 * GRADE 证据质量。用于资产版本层保存审核时的证据强度快照。
 */
public enum GradeEvidenceQuality {
    /** 高质量证据 */
    HIGH,
    /** 中等质量证据 */
    MODERATE,
    /** 低质量证据 */
    LOW,
    /** 极低质量证据 */
    VERY_LOW
}
