package com.medkernel.engine.llm;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
  * 场景路由与脱敏策略发布前校验请求对象。
  */
public record ModelPolicyValidateRequest(
    @NotBlank(message = "能力代码不能为空")
    String capabilityCode,

    @NotBlank(message = "路由策略不能为空")
    String routeStrategy,

    String desensitizeStrategy,

    String expectedSchema,

    List<String> fallbackOrder,

    Integer timeoutMs,

    Integer rateLimitPerMinute
) {}
