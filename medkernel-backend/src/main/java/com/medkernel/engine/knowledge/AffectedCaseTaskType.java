package com.medkernel.engine.knowledge;

/**
 * 知识失效后派发的影响处置任务类型。对应 {@code mk_knowledge_affected_case_task.task_type} CHECK 约束。
 */
public enum AffectedCaseTaskType {
    /** 医师或医务处复核已受影响的临床使用范围 */
    PHYSICIAN_REVIEW,
    /** 运行资产依赖复核与运行修订重发 */
    ASSET_DEPENDENCY_REVIEW,
    /** 图谱、搜索、Dify 或外部站点同步告警 */
    SYNC_ALERT
}
