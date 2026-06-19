package com.medkernel.engine.knowledge.production.initialization;

/** 知识初始化发行版类型。 */
public enum InitializationReleaseType {
    /** 稳定 canonical ID、数据元、术语、值集、单位、主数据和来源治理底座。 */
    FOUNDATION,
    /** 指南、药学、护理、报告、诊断等临床正文候选。 */
    CLINICAL_CONTENT,
    /** 规则、路径、推荐、随访和可执行构件。 */
    COMPOSITE
}
