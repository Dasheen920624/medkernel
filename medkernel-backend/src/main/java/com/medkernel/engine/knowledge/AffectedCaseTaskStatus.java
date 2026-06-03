package com.medkernel.engine.knowledge;

/**
 * 影响处置任务状态。对应 {@code mk_knowledge_affected_case_task.status} CHECK 约束。
 */
public enum AffectedCaseTaskStatus {
    /** 待人工复核或补同步 */
    OPEN,
    /** 正在处理 */
    IN_PROGRESS,
    /** 已完成并留证 */
    DONE,
    /** 经复核无需处置 */
    CANCELLED
}
