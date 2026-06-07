package com.medkernel.engine.evaluation;

/**
 * 评估指标状态枚举。
 *
 * <p>取值含义：{@code DRAFT} 草稿、{@code PENDING_REVIEW} 待审核、{@code PUBLISHED} 已发布、
 * {@code GRAY} 灰度、{@code ACTIVE} 生效、{@code OFFLINE} 下线、{@code ARCHIVED} 归档。
 */
public enum EvaluationIndicatorStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    GRAY,
    ACTIVE,
    OFFLINE,
    ARCHIVED
}
