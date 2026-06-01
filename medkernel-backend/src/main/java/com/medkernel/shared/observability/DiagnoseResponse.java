package com.medkernel.shared.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MedKernel v1.0 GA · GA-ENG-OBS-01 一键诊断响应。
 *
 * <p>各引擎实体 diagnose 端点统一返回本结构，自描述含状态历史、审计列表、
 * 关联实体、payload 元信息与跳转链接。
 */
public record DiagnoseResponse(
    String entityType,
    String entityId,
    String tenantId,
    String currentStatus,
    Object entity,
    List<StateTransitionEntry> stateHistory,
    List<AuditEventSummary> auditEvents,
    Map<String, List<String>> relatedEntities,
    PayloadSummary payloadSummary,
    String traceId,
    DiagnoseLinks links,
    ExecutionSummary executionSummary
) {

    public DiagnoseResponse(
            String entityType,
            String entityId,
            String tenantId,
            String currentStatus,
            Object entity,
            List<StateTransitionEntry> stateHistory,
            List<AuditEventSummary> auditEvents,
            Map<String, List<String>> relatedEntities,
            PayloadSummary payloadSummary,
            String traceId,
            DiagnoseLinks links) {
        this(
            entityType, entityId, tenantId, currentStatus,
            entity, stateHistory, auditEvents, relatedEntities,
            payloadSummary, traceId, links, null
        );
    }

    public record StateTransitionEntry(
        String fromStatus,
        String toStatus,
        String reason,
        String actor,
        String traceId,
        TransitionError error,
        Instant occurredAt
    ) {}

    public record AuditEventSummary(
        String action,
        String resourceType,
        String resourceId,
        String summary,
        String traceId,
        Instant occurredAt
    ) {}

    public record PayloadSummary(
        String digest,
        long sizeBytes,
        String contentType,
        String storageType,
        String fetchUri
    ) {}

    public record DiagnoseLinks(
        String self,
        String fetchPayload,
        String traceTimeline
    ) {}

    public record ExecutionSummary(
        String matchedRuleId,
        String matchedVersionId,
        Long durationMs,
        String degradationReason
    ) {}
}
