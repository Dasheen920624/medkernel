package com.medkernel.shared.runtime.task;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * SYS-05 运行任务死信权威记录，保留失败证据并支持人工回放。
 */
@Table("sys_task_dead_letter")
public record RuntimeTaskDeadLetterRecord(
    @Id Long id,
    @Column("dead_letter_id") String deadLetterId,
    @Column("tenant_id") String tenantId,
    @Column("org_path") String orgPath,
    @Column("task_id") String taskId,
    @Column("task_mode") String taskMode,
    @Column("task_type") String taskType,
    @Column("payload_storage_type") String payloadStorageType,
    @Column("payload_uri") String payloadUri,
    @Column("payload_digest") String payloadDigest,
    @Column("payload_size_bytes") Long payloadSizeBytes,
    @Column("total_count") Integer totalCount,
    @Column("retry_count") Integer retryCount,
    @Column("failure_details_json") String failureDetailsJson,
    @Column("error_code") String errorCode,
    @Column("message") String message,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("replayed_at") Instant replayedAt,
    @Column("replayed_by") String replayedBy,
    @Column("replay_task_id") String replayTaskId
) {

    public RuntimeTaskDeadLetterRecord withId(Long newId) {
        return new RuntimeTaskDeadLetterRecord(newId, deadLetterId, tenantId, orgPath, taskId, taskMode,
            taskType, payloadStorageType, payloadUri, payloadDigest, payloadSizeBytes, totalCount, retryCount,
            failureDetailsJson, errorCode, message, traceId, createdAt, createdBy, updatedAt, updatedBy,
            replayedAt, replayedBy, replayTaskId);
    }

    public RuntimeTaskDeadLetterRecord withReplay(Instant replayedAt, String actor, String replayTaskId) {
        return new RuntimeTaskDeadLetterRecord(id, deadLetterId, tenantId, orgPath, taskId, taskMode,
            taskType, payloadStorageType, payloadUri, payloadDigest, payloadSizeBytes, totalCount, retryCount,
            failureDetailsJson, errorCode, message, traceId, createdAt, createdBy, replayedAt, actor,
            replayedAt, actor, replayTaskId);
    }
}
