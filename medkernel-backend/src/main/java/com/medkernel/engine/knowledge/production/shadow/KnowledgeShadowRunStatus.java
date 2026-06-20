package com.medkernel.engine.knowledge.production.shadow;

/**
 * 生成期影子评测状态（AIK-STD-06）。
 *
 * <p>{@code NOT_READY} 表示缺真实基准集或影子条件，必须阻断提审；{@code FAILED} 表示评测未达阈值；
 * {@code PASSED} 表示可提审；{@code PENDING_REVIEW} 表示含高风险/红线用例，或严格 B0 非模型待编著骨架
 * 未执行模型评测，须在人工审核中重点复核。
 */
public enum KnowledgeShadowRunStatus {
    NOT_READY,
    FAILED,
    PASSED,
    PENDING_REVIEW
}
