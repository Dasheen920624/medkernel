package com.medkernel.engine.integration.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 接口集成流日志及死信队列实体 (IntegrationMessageLog) - Spring Data JDBC Record 风格。
 */
@Table("integration_message_log")
public record IntegrationMessageLog(
    @Id Long id,
    @Column("message_id") String messageId,
    @Column("tenant_id") String tenantId,
    @Column("trace_id") String traceId,
    @Column("direction") String direction, // INBOUND, OUTBOUND
    @Column("system_name") String systemName,
    @Column("protocol_type") String protocolType,
    @Column("payload_summary") String payloadSummary,
    @Column("payload") String payload,
    @Column("status") String status, // SUCCESS, FAILED, RETRYING, NOT_CONNECTED, DEAD_LETTER
    @Column("retry_count") Integer retryCount,
    @Column("max_retries") Integer maxRetries,
    @Column("error_message") String errorMessage,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    public IntegrationMessageLog withRetry(String newStatus, Integer newRetryCount, String newError) {
        return new IntegrationMessageLog(id, messageId, tenantId, traceId, direction, systemName, protocolType, payloadSummary, payload, newStatus, newRetryCount, maxRetries, newError, createdAt, createdBy, Instant.now(), updatedBy);
    }
}
