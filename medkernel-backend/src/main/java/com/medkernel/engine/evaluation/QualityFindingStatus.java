package com.medkernel.engine.evaluation;

/**
 * 质量问题状态枚举。
 *
 * <p>取值含义：{@code NEW} 新发现、{@code ASSIGNED} 已派单、{@code REMEDIATING} 整改中、
 * {@code CLOSED} 已关闭、{@code WAIVED} 已豁免。
 */
public enum QualityFindingStatus {
    NEW,
    ASSIGNED,
    REMEDIATING,
    CLOSED,
    WAIVED
}
