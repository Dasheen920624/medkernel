package com.medkernel.engine.workflow;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 统一通知实体。
 */
@Table("mk_engine_notification")
public record WorkflowNotification(
    @Id Long id,
    @Column("notification_id") String notificationId,
    @Column("tenant_id") String tenantId,
    @Column("org_unit_id") String orgUnitId,
    @Column("source_type") WorkflowNotificationSourceType sourceType,
    @Column("source_id") String sourceId,
    @Column("dedupe_key") String dedupeKey,
    String title,
    String message,
    @Column("notification_level") WorkflowNotificationLevel level,
    WorkflowNotificationStatus status,
    @Column("recipient_id") String recipientId,
    @Column("recipient_role") String recipientRole,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("deep_link") String deepLink,
    @Column("read_at") Instant readAt,
    @Column("read_by") String readBy,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    public WorkflowNotification(
            Long id,
            String notificationId,
            String tenantId,
            WorkflowNotificationSourceType sourceType,
            String sourceId,
            String dedupeKey,
            String title,
            String message,
            WorkflowNotificationLevel level,
            WorkflowNotificationStatus status,
            String recipientId,
            String recipientRole,
            String patientId,
            String encounterId,
            String deepLink,
            Instant readAt,
            String readBy,
            String traceId,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy) {
        this(
            id,
            notificationId,
            tenantId,
            null,
            sourceType,
            sourceId,
            dedupeKey,
            title,
            message,
            level,
            status,
            recipientId,
            recipientRole,
            patientId,
            encounterId,
            deepLink,
            readAt,
            readBy,
            traceId,
            createdAt,
            createdBy,
            updatedAt,
            updatedBy);
    }
}
