package com.medkernel.engine.authoring;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 创作批量任务逐项执行结果。
 */
@Table("mk_engine_authoring_batch_item")
public record AuthoringBatchItem(
    @Id Long id,
    @Column("job_id") String jobId,
    @Column("tenant_id") String tenantId,
    @Column("item_id") String itemId,
    AuthoringBatchItemStatus status,
    @Column("target_type") String targetType,
    @Column("target_id") String targetId,
    @Column("result_json") String resultJson,
    @Column("rollback_ref") String rollbackRef,
    @Column("error_code") String errorCode,
    String message,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
    public AuthoringBatchItem withId(Long value) {
        return new AuthoringBatchItem(
            value, jobId, tenantId, itemId, status, targetType, targetId, resultJson,
            rollbackRef, errorCode, message, createdAt, createdBy, traceId);
    }
}
