package com.medkernel.engine.llm;

import java.util.List;

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
    List<String> fallbackOrder,
    Integer timeoutMs,
    Integer rateLimitPerMinute,
    String policyScopeType,
    String policyScopeRef,
    Boolean inherited,
    Boolean configured,
    Boolean fallbackAvailable,
    String fallbackReason
) {}
