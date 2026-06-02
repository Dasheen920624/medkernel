package com.medkernel.engine.pkg;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 同步执行日志实体。
 *
 * <p>保存同步执行状态、错误码、重试与加密签名存证。
 */
@Table("sync_log")
public record SyncLog(
    @Id Long id,
    @Column("log_id") String logId,
    @Column("tenant_id") String tenantId,
    @Column("plan_id") String planId,
    @Column("target_id") String targetId,
    SyncLogStatus status,
    @Column("error_code") String errorCode,
    @Column("error_message") String errorMessage,
    @Column("retry_count") int retryCount,
    @Column("sync_evidence") String syncEvidence,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public SyncLog withStatus(SyncLogStatus newStatus, String errCode, String errMsg) {
        return new SyncLog(
            id, logId, tenantId, planId, targetId, newStatus,
            errCode, errMsg, retryCount, syncEvidence,
            createdAt, createdBy, Instant.now(), updatedBy, traceId
        );
    }

    public SyncLog withRetry(int newCount, SyncLogStatus newStatus, String errCode, String errMsg) {
        return new SyncLog(
            id, logId, tenantId, planId, targetId, newStatus,
            errCode, errMsg, newCount, syncEvidence,
            createdAt, createdBy, Instant.now(), updatedBy, traceId
        );
    }
}
