package com.medkernel.engine.context;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 临床事件流水。snapshot 上游入口或下游触发记录。
 */
@Table("clinical_event")
public record ClinicalEvent(
    @Id Long id,
    @Column("event_id") String eventId,
    @Column("tenant_id") String tenantId,
    @Column("event_type") ClinicalEventType eventType,
    @Column("trigger_point") ClinicalEventTriggerPoint triggerPoint,
    @Column("idempotency_key") String idempotencyKey,
    @Column("callback_webhook_id") String callbackWebhookId,
    @Column("org_scope_json") String orgScopeJson,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("source_system") String sourceSystem,
    @Column("package_version") String packageVersion,
    @Column("payload_digest") String payloadDigest,
    @Column("occurred_at") Instant occurredAt,
    @Column("received_at") Instant receivedAt,
    @Column("snapshot_id") String snapshotId,
    @Column("processing_status") ClinicalEventStatus processingStatus,
    @Column("error_code") String errorCode,
    @Column("error_class") String errorClass,
    @Column("retry_count") Integer retryCount,
    @Column("root_event_id") String rootEventId,
    @Column("trace_id") String traceId
) {}
