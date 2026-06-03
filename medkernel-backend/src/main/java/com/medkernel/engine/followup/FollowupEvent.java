package com.medkernel.engine.followup;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 随访异常与结果回流事件实体。
 */
@Table("followup_event")
public record FollowupEvent(
    @Id Long id,
    @Column("event_id") String eventId,
    @Column("tenant_id") String tenantId,
    @Column("plan_id") String planId,
    @Column("event_type") FollowupEventType eventType,
    String payload,
    @Column("triggered_by") String triggeredBy,
    @Column("idempotency_key") String idempotencyKey,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public FollowupEvent(
            Long id,
            String eventId,
            String tenantId,
            String planId,
            FollowupEventType eventType,
            String payload,
            String triggeredBy,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, eventId, tenantId, planId, eventType, payload, triggeredBy, null,
            createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
