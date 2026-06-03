package com.medkernel.engine.integration.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 第三方 Webhook 入站消息请求契约。
 *
 * <p>签名覆盖整个 Record 序列化载荷，避免外部系统只签部分字段造成重放或字段替换风险。
 */
public record WebhookInboundRequestDto(
    @NotBlank(message = "消息ID不能为空")
    String messageId,

    String traceId,

    @NotBlank(message = "适配器ID不能为空")
    String adapterId,

    @NotBlank(message = "来源系统不能为空")
    String sourceSystem,

    @NotBlank(message = "事件类型不能为空")
    String eventType,

    @NotNull(message = "入站载荷不能为空")
    JsonNode payload
) {
}
