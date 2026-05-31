package com.medkernel.shared.audit.persistence;

import java.time.Instant;

/**
 * 审计事件持久化模型（对应 {@code audit_event} 表 + V5 哈希链列 + V9 outcome/errorCode + V30 完整审计脊柱字段）。
 *
 * <p>与 {@link com.medkernel.shared.audit.AuditEvent} 区分：
 * <ul>
 *   <li>{@code AuditEvent} 是 in-process 事件契约，由业务线发布</li>
 *   <li>{@code AuditEventRecord} 是落库后的快照，包含链路前驱、签名和状态</li>
 * </ul>
 *
 * <p>不暴露给前端；前端展示由 {@code AuditEventView} 投影。
 */
public record AuditEventRecord(
    Long id,
    String eventId,
    String traceId,
    Instant occurredAt,
    String actorUserId,
    String action,
    String resourceType,
    String resourceId,
    String summary,
    String payloadDigest,
    String tenantId,
    String hospitalId,
    String departmentId,
    String prevEventId,
    String prevSignature,
    String signature,
    String status,
    String outcome,
    String errorCode,
    Instant createdAt,
    String actorRoles,
    String orgPath,
    String environmentKey,
    String beforeSnapshot,
    String afterSnapshot,
    String dedupeKey
) {

    public AuditEventRecord(Long id,
                            String eventId,
                            String traceId,
                            Instant occurredAt,
                            String actorUserId,
                            String action,
                            String resourceType,
                            String resourceId,
                            String summary,
                            String payloadDigest,
                            String tenantId,
                            String hospitalId,
                            String departmentId,
                            String prevEventId,
                            String prevSignature,
                            String signature,
                            String status,
                            String outcome,
                            String errorCode,
                            Instant createdAt) {
        this(id, eventId, traceId, occurredAt, actorUserId, action, resourceType, resourceId,
            summary, payloadDigest, tenantId, hospitalId, departmentId, prevEventId, prevSignature,
            signature, status, outcome, errorCode, createdAt, null, null, null, null, null, null);
    }
}
