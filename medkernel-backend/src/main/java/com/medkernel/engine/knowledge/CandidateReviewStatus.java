package com.medkernel.engine.knowledge;

/**
 * 知识候选审核分流状态。
 */
public enum CandidateReviewStatus {
    /** 候选仅供替换审核，不参与临床执行 */
    PENDING_REPLACEMENT_REVIEW,
    /** 重复候选已跳过去重，不产生审核待办 */
    DUPLICATE_SKIPPED,
    /** 审核通过，后续交由 SYS-08 原子替换 */
    APPROVED,
    /** 审核拒绝并留档 */
    REJECTED,
    /** 已退修，退回生产者修订重提，退出审核台队列 */
    RETURNED
}
