package com.medkernel.engine.knowledge;

/**
 * 知识候选审核结论。
 */
public enum KnowledgeCandidateReviewDecision {
    APPROVE,
    REJECT,
    /** 退修：可修订，退回生产者并附修订意见，期待修订重提（区别于 REJECT 永久拒绝） */
    RETURN
}
