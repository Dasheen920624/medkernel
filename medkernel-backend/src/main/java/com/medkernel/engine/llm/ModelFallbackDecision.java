package com.medkernel.engine.llm;

/**
 * LLM-02 单次降级裁决。
 *
 * @param routeStrategy 原始路由策略
 * @param sourceMode 原始模型层级（B0/B1/B2）
 * @param fallbackMode 降级后的模型层级
 * @param fallbackUsed 是否发生降级
 * @param reason 稳定中文归因，包含触发码
 * @param retryable 该触发是否建议重试
 */
public record ModelFallbackDecision(
    String routeStrategy,
    String sourceMode,
    String fallbackMode,
    boolean fallbackUsed,
    String reason,
    boolean retryable
) {}
