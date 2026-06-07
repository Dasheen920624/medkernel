package com.medkernel.engine.integration.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Webhook 订阅安全配置实体 (IntegrationWebhookConfig) - Spring Data JDBC Record 风格。
 */
@Table("integration_webhook_config")
public record IntegrationWebhookConfig(
    @Id Long id,
    @Column("webhook_id") String webhookId,
    @Column("tenant_id") String tenantId,
    @Column("name") String name,
    @Column("callback_url") String callbackUrl,
    @Column("secret_cipher") String secretCipher,
    @Column("events_subscribed") String eventsSubscribed,
    @Column("status") String status, // ACTIVE, SUSPENDED
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
