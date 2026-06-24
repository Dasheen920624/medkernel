package com.medkernel.engine.terminology;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * API-04 术语候选生成异步任务。
 *
 * <p>任务只保存生成参数、进度和分页入口；候选明细仍写入 {@code mapping_candidate}，
 * 并用 {@code generation_job_code} 建立可追溯关系，避免同步响应返回大批量明细。
 */
@Table("mk_term_candidate_generation_job")
public record TerminologyCandidateGenerationJob(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("source_system") String sourceSystem,
    @Column("minimum_score") Double minimumScore,
    @Column("semantic_assist_enabled") Boolean semanticAssistEnabled,
    @Column("requested_by") String requestedBy,
    @Column("status") TerminologyCandidateGenerationJobStatus status,
    @Column("progress") Integer progress,
    @Column("generated_count") Integer generatedCount,
    @Column("candidate_page_uri") String candidatePageUri,
    @Column("error_message") String errorMessage,
    @Column("created_at") Instant createdAt,
    @Column("started_at") Instant startedAt,
    @Column("completed_at") Instant completedAt
) {

    boolean isTerminal() {
        return status != null && status.isTerminal();
    }
}
