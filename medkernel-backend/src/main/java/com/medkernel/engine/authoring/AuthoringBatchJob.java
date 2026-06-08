package com.medkernel.engine.authoring;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 创作批量任务聚合根。
 */
@Table("mk_engine_authoring_batch_job")
public record AuthoringBatchJob(
    @Id Long id,
    @Column("job_id") String jobId,
    @Column("tenant_id") String tenantId,
    @Column("job_type") AuthoringBatchJobType jobType,
    AuthoringBatchJobStatus status,
    @Column("total_count") int totalCount,
    @Column("success_count") int successCount,
    @Column("failure_count") int failureCount,
    @Column("retryable_count") int retryableCount,
    @Column("request_summary_json") String requestSummaryJson,
    @Column("result_summary_json") String resultSummaryJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public AuthoringBatchJob withId(Long value) {
        return new AuthoringBatchJob(
            value, jobId, tenantId, jobType, status, totalCount, successCount, failureCount,
            retryableCount, requestSummaryJson, resultSummaryJson, createdAt, createdBy,
            updatedAt, updatedBy, traceId);
    }

    public AuthoringBatchJob completed(
            AuthoringBatchJobStatus finalStatus,
            int successes,
            int failures,
            int retryable,
            String summaryJson,
            Instant completedAt,
            String actor) {
        return new AuthoringBatchJob(
            id, jobId, tenantId, jobType, finalStatus, totalCount, successes, failures,
            retryable, requestSummaryJson, summaryJson, createdAt, createdBy,
            completedAt, actor, traceId);
    }
}
