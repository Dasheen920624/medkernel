package com.medkernel.engine.llm.eval;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型服务与版本医学回归评测运行结果（LLM-07 FR-5）。
 *
 * <p>{@code status}：全部安全评测通过为 {@code PASSED}，可作为 {@code ENG-LLM-008} 上线门禁证据；
 * {@code FAILED} 阻断上线。{@code fakeCitationDetected}/{@code redLineBreach} 任一为真即 FAILED。
 * 每次运行绑定不可变的 {@code releaseFingerprint}，历史交付内容的评测结果不得用于当前部署放行。
 */
@Table("mk_llm_eval_run")
public record ModelEvalRun(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("provider_code") String providerCode,
    @Column("model_version") String modelVersion,
    @Column("capability_code") String capabilityCode,
    @Column("prompt_version") String promptVersion,
    @Column("tool_version") String toolVersion,
    @Column("release_fingerprint") String releaseFingerprint,
    @Column("total_cases") int totalCases,
    @Column("passed_cases") int passedCases,
    @Column("failed_cases") int failedCases,
    @Column("quality_score") Double qualityScore,
    @Column("terminology_score") Double terminologyScore,
    @Column("fake_citation_detected") String fakeCitationDetected,
    @Column("red_line_breach") String redLineBreach,
    @Column("hallucination_detected") String hallucinationDetected,
    @Column("status") String status,
    @Column("case_summary_json") String caseSummaryJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
