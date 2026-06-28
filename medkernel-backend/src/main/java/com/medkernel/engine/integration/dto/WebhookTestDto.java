package com.medkernel.engine.integration.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 外部 Webhook 签名与连通性验证 DTO Record。
 *
 * <p>用于封装在 Webhook 订阅通道验证中传递验证报文及 JSR-380 输入校验规则。
 */
public record WebhookTestDto(
    @NotBlank(message = "WebhookID 不能为空")
    String webhookId,

    @NotBlank(message = "验证报文内容不能为空")
    String payload
) {}
