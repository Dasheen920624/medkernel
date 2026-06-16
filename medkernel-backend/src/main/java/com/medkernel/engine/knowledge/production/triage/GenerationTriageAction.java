package com.medkernel.engine.knowledge.production.triage;

/**
 * AIK-STD-10 生成期分流去向。
 */
public enum GenerationTriageAction {
    /** 正常进入审核链。 */
    SUBMIT_REVIEW,
    /** 内容重复，记录依据后跳过物化/审核。 */
    SKIP_DUPLICATE,
    /** 进入合并对照审核。 */
    MERGE_REVIEW,
    /** 进入重大升级审核。 */
    UPGRADE_REVIEW,
    /** 进入冲突仲裁审核。 */
    CONFLICT_REVIEW,
    /** 进入降级风险审核。 */
    DOWNGRADE_REVIEW,
    /** 进入废止/退役审核。 */
    RETIREMENT_REVIEW,
    /** 判定依据不足，人工分流。 */
    MANUAL_REVIEW
}
