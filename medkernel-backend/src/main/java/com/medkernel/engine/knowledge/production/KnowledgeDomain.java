package com.medkernel.engine.knowledge.production;

/**
 * 知识候选生产领域（AIK-STD-13 PR3，FR-6 会签领域归类）。
 *
 * <p>医学领域与结构资产类型（{@code VersionedAssetType}）正交：药学＝领域不是类型，
 * 药品说明书走 {@code KNOWLEDGE} 资产、DDI 走 {@code RULE} 资产，经本枚举区分领域并路由药事安全人员。
 */
public enum KnowledgeDomain {
    CLINICAL,
    PHARMACY,
    TERMINOLOGY_REPORT,
    EVALUATION_INSURANCE,
    GENERAL
}
