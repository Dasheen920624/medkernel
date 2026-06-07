package com.medkernel.engine.integration.dto;

import java.time.Instant;

/**
 * Webhook 创建结果。
 *
 * <p>共享密钥仅在创建成功时返回一次；后续列表、详情和测试接口均不可再次读取。
 */
public record WebhookCreateResponse(
    Long id,
    String webhookId,
    String name,
    String callbackUrl,
    String eventsSubscribed,
    String status,
    Instant createdAt,
    Instant updatedAt,
    String sharedSecret
) {
}
