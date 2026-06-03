package com.medkernel.engine.knowledge;

/**
 * 知识版本失效处置状态。对应 {@code mk_knowledge_invalidation.status} CHECK 约束。
 */
public enum KnowledgeInvalidationStatus {
    /** 已触发限制，影响任务尚未全部关闭 */
    OPEN,
    /** 已完成影响复核与补同步 */
    RESOLVED,
    /** 人工确认该失效记录无需继续处置 */
    CANCELLED
}
