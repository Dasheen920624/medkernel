package com.medkernel.engine.knowledge;

/**
 * 知识审核后的回流动作；审核台只登记动作，不直接生产新知识。
 */
public enum KnowledgeReviewFollowupAction {
    NONE,
    CREATE_REVISION_CANDIDATE,
    REQUEST_SOURCE_EVIDENCE,
    MARK_FALSE_POSITIVE,
    ARCHIVE_REJECTED
}
