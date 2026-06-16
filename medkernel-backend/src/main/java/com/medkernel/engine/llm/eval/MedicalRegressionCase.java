package com.medkernel.engine.llm.eval;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 医学回归评测基准用例（LLM-07 FR-1）。
 *
 * <p>{@code expectedPhrase} 为期望产出关键短语；{@code redLineType} 非空表示红线用例（越线判 FAIL）；
 * {@code citationRequired}=Y 表示产出须带可回溯真实引用，否则判假引用 FAIL。
 */
@Table("mk_llm_regression_case")
public record MedicalRegressionCase(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("case_input") String caseInput,
    @Column("expected_phrase") String expectedPhrase,
    @Column("red_line_type") String redLineType,
    @Column("source_reference") String sourceReference,
    @Column("citation_required") String citationRequired,
    @Column("case_version") String caseVersion,
    @Column("enabled_flag") String enabledFlag,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    public static final String DEFAULT_CAPABILITY_CODE = "rule.draft";

    public boolean redLine() {
        return redLineType != null && !redLineType.isBlank();
    }

    public boolean requiresCitation() {
        return "Y".equalsIgnoreCase(citationRequired);
    }
}
