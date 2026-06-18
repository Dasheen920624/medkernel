package com.medkernel.engine.knowledge;

/**
 * 知识版本失效类型。对应 {@code mk_knowledge_invalidation.invalidation_type} CHECK 约束。
 */
public enum KnowledgeInvalidationType {
    /** 普通审核通过新版后，旧权威版本被原子替换并退出新临床决策 */
    SUPERSEDED_REPLACEMENT,
    /** 安全风险确认后的紧急撤回 */
    EMERGENCY_WITHDRAW,
    /** 上游来源强制召回 */
    SOURCE_RECALL,
    /** 禁忌证或警示变化导致的立即限制 */
    SAFETY_ALERT
}
