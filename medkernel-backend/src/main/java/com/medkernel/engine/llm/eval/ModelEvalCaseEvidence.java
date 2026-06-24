package com.medkernel.engine.llm.eval;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 医学回归评测逐用例不可变证据。
 *
 * <p>保存评测时的用例快照、模型真实输出、来源引用核验和红线裁决，供负责人核查。
 * 不保存 provider 凭据、提示词正文或患者身份数据。
 */
@Table("mk_llm_eval_case_evidence")
public record ModelEvalCaseEvidence(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("run_id") Long runId,
    @Column("regression_case_id") Long regressionCaseId,
    @Column("case_version") String caseVersion,
    @Column("case_input") String caseInput,
    @Column("expected_phrase") String expectedPhrase,
    @Column("red_line_type") String redLineType,
    @Column("source_reference") String sourceReference,
    @Column("output_content") String outputContent,
    @Column("source_citations") String sourceCitations,
    @Column("expected_phrase_hit") String expectedPhraseHit,
    @Column("citation_required") String citationRequired,
    @Column("citation_verified") String citationVerified,
    @Column("red_line_case") String redLineCase,
    @Column("red_line_breach") String redLineBreach,
    @Column("passed_flag") String passedFlag,
    @Column("failure_reasons_json") String failureReasonsJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {
    public boolean passed() {
        return "Y".equalsIgnoreCase(passedFlag);
    }
}
