package com.medkernel.engine.knowledge;

/**
 * 知识审核反馈类型，用于把审核结论结构化回流给生产台或治理任务。
 */
public enum KnowledgeReviewFeedbackType {
    ACCEPTED,
    NOT_ADOPTED,
    CONTENT_GAP,
    SOURCE_BLANK,
    FALSE_POSITIVE
}
