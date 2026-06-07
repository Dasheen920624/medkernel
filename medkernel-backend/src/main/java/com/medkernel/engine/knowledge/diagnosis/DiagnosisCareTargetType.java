package com.medkernel.engine.knowledge.diagnosis;

/**
 * 诊疗指针目标类型：明确目标所属引擎，避免以无类型字符串猜测路由。
 */
public enum DiagnosisCareTargetType {
    /** 规则资产。 */
    RULE,
    /** 知识资产。 */
    KNOWLEDGE,
    /** 专病路径。 */
    PATHWAY
}
