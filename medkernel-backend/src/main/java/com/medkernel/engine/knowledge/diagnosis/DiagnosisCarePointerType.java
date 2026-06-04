package com.medkernel.engine.knowledge.diagnosis;

/** 诊疗指针类型：确诊后指向治疗 / 检查（规则·知识）或专病路径（恒软建议）。 */
public enum DiagnosisCarePointerType {
    /** 治疗（药品 / 方案）。 */
    TREATMENT,
    /** 检查 / 补充检验。 */
    WORKUP,
    /** 专病路径。 */
    PATHWAY
}
