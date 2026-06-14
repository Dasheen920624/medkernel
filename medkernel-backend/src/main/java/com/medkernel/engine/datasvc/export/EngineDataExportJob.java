package com.medkernel.engine.datasvc.export;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 引擎数据服务层异步导出作业（DATASVC-01）。{@code job_code} 是对外可见的 UUID（不暴露 DB 主键）。
 *
 * <p>把三组 D2 去标识聚合读模型（规则/知识/临床信号使用统计）经 SYS-06 导出审批闸控制后异步导出为 CSV。
 * {@code approvalId}/{@code idempotencyKey}/{@code requestSnapshot} 三锚点用于与导出审批申请校验一致，
 * 作业不绕审批；单机线程池执行，结果文件 TTL 默认 7 天。
 */
@Table("mk_engine_data_export_job")
public record EngineDataExportJob(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("requested_by") String requestedBy,
    @Column("export_type") EngineDataExportType exportType,
    @Column("status") ExportJobStatus status,
    @Column("progress") Integer progress,
    @Column("result_uri") String resultUri,
    @Column("item_count") Long itemCount,
    @Column("error_message") String errorMessage,
    @Column("approval_id") String approvalId,
    @Column("idempotency_key") String idempotencyKey,
    @Column("request_snapshot") String requestSnapshot,
    @Column("created_at") Instant createdAt,
    @Column("started_at") Instant startedAt,
    @Column("completed_at") Instant completedAt,
    @Column("expires_at") Instant expiresAt
) {

    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }
}
