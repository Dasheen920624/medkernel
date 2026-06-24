package com.medkernel.engine.integration.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.context.canonical.ClinicalSetting;
import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    @NotNull(message = "事件类型不能为空")
    ClinicalEventType eventType,

    @NotBlank(message = "患者ID不能为空")
    @Size(max = 64, message = "患者ID长度不能超过64")
    String patientId,

    @Size(max = 64, message = "就诊ID长度不能超过64")
    String encounterId,

    @NotNull(message = "临床场景不能为空")
    ClinicalSetting clinicalSetting,

    @NotNull(message = "临床触发点不能为空")
    ClinicalEventTriggerPoint triggerPoint,

    @NotNull(message = "事件发生时间不能为空")
    Instant occurredAt,

    @NotNull(message = "入站载荷不能为空")
    JsonNode payload
) {
}
