package com.medkernel.compliance.audit;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.medkernel.shared.audit.AuditActorClassifier;
import com.medkernel.shared.audit.persistence.AuditEventRecord;

/**
 * 合规审计列表 / 快照接口对外展示 DTO。
 *
 * <p>从持久化记录 {@link AuditEventRecord} 投影；不暴露内部主键 {@code id} 之外的存储细节。
 * {@code summary} 面向人工阅读，{@code actionCode} / {@code resourceType} / {@code resourceId}
 * 面向可解释验签与筛选。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEvent(
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

    public static AuditEvent from(AuditEventRecord record) {
        String summary = record.summary() == null
            ? record.action() + " " + record.resourceType() + "/" + record.resourceId()
            : record.summary();
        return new AuditEvent(
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
