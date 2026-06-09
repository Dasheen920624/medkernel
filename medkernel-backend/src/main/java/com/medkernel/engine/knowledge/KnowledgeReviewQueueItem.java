package com.medkernel.engine.knowledge;

/**
 * 复审队列中的知识身份、权威版本与时效结论。
 */
public record KnowledgeReviewQueueItem(
    KnowledgeIdentity identity,
    KnowledgeAssetVersion version,
    KnowledgeReviewStatus status,
    long daysUntilDue
) {
}
