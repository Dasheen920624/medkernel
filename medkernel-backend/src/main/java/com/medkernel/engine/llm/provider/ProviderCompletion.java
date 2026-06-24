package com.medkernel.engine.llm.provider;

/**
 * 模型服务真实补全结果（LLM-08 FR-4）。
 *
 * <p>{@code modelVersion} 为模型服务返回的真实模型版本；{@code confidence}/{@code sourceCitations}
 * 仅在模型服务真实返回时填充，无则分别为 {@code null}/{@code "[]"}，绝不伪造（铁律 #1）。
 */
public record ProviderCompletion(
    String content,
    String modelVersion,
    Double confidence,
    String sourceCitations
) {}
