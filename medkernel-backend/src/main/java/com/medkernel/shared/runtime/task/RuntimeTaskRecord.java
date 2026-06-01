package com.medkernel.shared.runtime.task;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * SYS-05 运行任务关系库权威记录。
 */
@Table("sys_task")
public record RuntimeTaskRecord(
    @Id Long id,
    @Column("task_id") String taskId,
    @Column("tenant_id") String tenantId,
    @Column("org_path") String orgPath,
    @Column("task_mode") String mode,
    @Column("status") String status,
    @Column("task_type") String taskType,
    @Column("payload_storage_type") String payloadStorageType,
    @Column("payload_uri") String payloadUri,
    @Column("payload_digest") String payloadDigest,
    @Column("payload_size_bytes") Long payloadSizeBytes,
    @Column("total_count") Integer totalCount,
    @Column("success_count") Integer successCount,
    @Column("failure_count") Integer failureCount,
    @Column("retryable_count") Integer retryableCount,
    @Column("failure_details_json") String failureDetailsJson,
    @Column("message") String message,
    @Column("error_code") String errorCode,
    @Column("trace_id") String traceId,
    @Column("started_at") Instant startedAt,
    @Column("finished_at") Instant finishedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    public RuntimeTaskRecord withId(Long newId) {
        return new RuntimeTaskRecord(newId, taskId, tenantId, orgPath, mode, status, taskType,
            payloadStorageType, payloadUri, payloadDigest, payloadSizeBytes, totalCount, successCount,
            failureCount, retryableCount, failureDetailsJson, message, errorCode, traceId, startedAt,
            finishedAt, createdAt, createdBy, updatedAt, updatedBy);
    }

    public RuntimeTaskRecord withTerminalResult(RuntimeTaskExecutionResult result,
                                                String failureDetails,
                                                Instant finishedAt,
                                                String actor) {
        return new RuntimeTaskRecord(id, taskId, tenantId, orgPath, mode, result.status().name(), taskType,
            payloadStorageType, payloadUri, payloadDigest, payloadSizeBytes, result.totalCount(),
            result.successCount(), result.failureCount(), result.retryableCount(), failureDetails,
            result.message(), result.errorCode(), traceId, startedAt, finishedAt, createdAt, createdBy,
            finishedAt, actor);
    }
}
