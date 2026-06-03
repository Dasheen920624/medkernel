package com.medkernel.engine.knowledge;

/**
 * 影响处置任务目标类型。对应 {@code mk_knowledge_affected_case_task.target_type} CHECK 约束。
 */
public enum AffectedCaseTargetType {
    /** 知识版本本身，表示 scope 级复核而非伪造患者清单 */
    KNOWLEDGE_VERSION,
    /** 配置包或离线包依赖范围 */
    PACKAGE_DEPENDENCY,
    /** 图谱 / 搜索 / 外部同步范围 */
    SYNC_TARGET,
    /** 已有真实索引命中的患者病例，当前 B0 不伪造该类目标 */
    PATIENT_CASE,
    /** 已有真实索引命中的患者路径，当前 B0 不伪造该类目标 */
    PATIENT_PATHWAY
}
