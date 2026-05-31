package com.medkernel.compliance.audit;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.medkernel.shared.audit.AuditActorClassifier;
import com.medkernel.shared.audit.persistence.AuditEventRecord;

/**
 * 合规审计列表 / 快照接口对外展示 DTO。
 *
 * <p>从持久化记录 {@link AuditEventRecord} 投影；不暴露内部主键 {@code id} 之外的存储细节。
 * 历史字段命名（{@code action} = 文案，{@code user} = 操作人）保留以兼容旧的合规页面；
 * 新增 {@code actionCode} / {@code resourceType} / {@code resourceId} 给可解释验签使用。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEvent(
    String id,
    String eventId,
    Instant occurredAt,
    String user,
    String action,
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
            AuditActorClassifier.isSuperAdminAction(record.actorRoles())
        );
    }
}
