package com.medkernel.engine.llm;

import jakarta.validation.constraints.NotBlank;

/**
 * 模型能力路由策略保存请求。
 */
public record ModelPolicyUpsertRequest(
    @NotBlank(message = "路由策略不能为空")
    String routeStrategy,

    @NotBlank(message = "脱敏策略不能为空")
    String desensitizeStrategy,

    String expectedSchema
) {}
