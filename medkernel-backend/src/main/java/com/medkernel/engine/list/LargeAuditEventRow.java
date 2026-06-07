package com.medkernel.engine.list;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medkernel.shared.audit.AuditActorClassifier;
import com.medkernel.shared.audit.persistence.AuditEventRecord;

/**
 * 大列表审计事件的公开行模型。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LargeAuditEventRow(
    String id,
    String eventId,
    Instant occurredAt,
    String actorUserId,
    String summary,
    String actionCode,
    String resourceType,
    String resourceId,
    String traceId,
    String signature,
    String status,
    String actorRoles,
    String orgPath,
    String environmentKey,
    String outcome,
    String errorCode,
    String payloadDigest,
    String beforeSnapshot,
    String afterSnapshot,
    boolean superAdminAction
) {

    public static LargeAuditEventRow from(AuditEventRecord record) {
        String summary = record.summary() == null
            ? record.action() + " " + record.resourceType() + "/" + record.resourceId()
            : record.summary();
        return new LargeAuditEventRow(
            record.id() == null ? null : record.id().toString(),
            record.eventId(),
            record.occurredAt(),
            record.actorUserId(),
            summary,
            record.action(),
            record.resourceType(),
            record.resourceId(),
            record.traceId(),
            record.signature(),
            record.status(),
            record.actorRoles(),
            record.orgPath(),
            record.environmentKey(),
            record.outcome(),
            record.errorCode(),
            record.payloadDigest(),
            record.beforeSnapshot(),
            record.afterSnapshot(),
            AuditActorClassifier.isSuperAdminAction(record.actorRoles())
        );
    }
}
