package com.medkernel.engine.list;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 大规模数据异步导出任务实体。
 *
 * <p>用于存储后台 CSV 异步导出任务的进度、文件路径及元数据。
 */
@Table("mk_experience_export_task")
public record LargeListExportJob(
    @Id
    @Column("task_id")
    String jobId,
    @Column("tenant_id") String tenantId,
    @Column("resource_type") String resourceType,
    @Column("request_snapshot") String requestSnapshot,
    @Column("selected_scope") String selectedScope,
    @Column("status") String status,
    @Column("file_name") String fileName,
    @Column("file_path") String filePath,
    @Column("file_size") Long fileSize,
    @Column("error_message") String errorMessage,
    @Column("time_cost_ms") Long timeCostMs,
    @Column("trace_id") String traceId,
    @Column("audit_id") String auditId,
    @Column("idempotency_key") String idempotencyKey,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    /**
     * 辅助工厂方法，创建一个带有初始默认值的待执行任务。
     *
     * @param jobId 异步导出任务全局唯一ID
     * @param tenantId 租户ID
     * @param resourceType 导出的列表资源类型
     * @param requestSnapshot 导出请求快照 JSON
     * @param selectedScope 导出范围
     * @param traceId 请求链路追踪ID
     * @param idempotencyKey 幂等键
     * @param creator 创建人账号或系统标识
     * @return 初始的导出任务实体
     */
    public static LargeListExportJob createPending(
        String jobId,
        String tenantId,
        String resourceType,
        String requestSnapshot,
        String selectedScope,
        String traceId,
        String idempotencyKey,
        String creator
    ) {
        Instant now = Instant.now();
        return new LargeListExportJob(
            jobId,
            tenantId,
            resourceType,
            requestSnapshot,
            selectedScope,
            "PENDING",
            null,
            null,
            0L,
            null,
            0L,
            traceId,
            null,
            idempotencyKey,
            now,
            creator,
            now,
            creator
        );
    }
}
