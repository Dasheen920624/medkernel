package com.medkernel.engine.integration.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 第三方 Webhook 入站处理结果契约。
 *
 * <p>返回标准上下文字段映射结果与编码归一证据，不返回共享密钥。
 */
public record WebhookInboundResultDto(
    String messageId,
    String traceId,
    String webhookId,
    String adapterId,
    String status,
    JsonNode mappedPayload,
    int mappedFieldCount,
    int normalizedCodeCount,
    String clinicalEventId,
    String clinicalEventStatus,
    boolean idempotentReplay,
    List<String> warnings
) {
}
