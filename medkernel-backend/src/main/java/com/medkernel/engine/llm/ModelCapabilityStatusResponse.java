package com.medkernel.engine.llm;

/**
  * 模型能力可用状态响应对象。
  */
public record ModelCapabilityStatusResponse(
    String capabilityCode,
    String displayName,
    String description,
    String category,
    String routeStrategy,
    String desensitizeStrategy,
    String expectedSchema,
    Boolean configured,
    Boolean fallbackAvailable,
    String fallbackReason
) {}
