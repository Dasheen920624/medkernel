package com.medkernel.engine.integration.dto;

import java.time.Instant;

/**
 * Webhook 订阅公开配置。
 *
 * <p>只返回人工管理所需元数据，任何查询接口都不得返回共享密钥或密文。
 */
public record WebhookConfigResponse(
    Long id,
    String webhookId,
    String name,
    String callbackUrl,
    String eventsSubscribed,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
