package com.medkernel.engine.knowledge.production.shadow;

/**
 * 影子评测对候选能否进入人工审核的裁决。
 *
 * <p>只描述本次评测结论；完整指标保存在 {@link KnowledgeShadowRun}。
 */
public record KnowledgeShadowDecision(
    Long runId,
    KnowledgeShadowRunStatus status,
    boolean readyForReview,
    String basis
) {
}
