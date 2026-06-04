package com.medkernel.engine.cdss.risk;

import java.time.Instant;

import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * OPT-03 CDSS/器械风险分级矩阵规则。
 */
@Table("mk_engine_cdss_risk_matrix")
public record CdssRiskMatrixRule(
    @Id Long id,
    @Column("matrix_id") String matrixId,
    @Column("tenant_id") String tenantId,
    @Column("trigger_point") String triggerPoint,
    @Column("severity_level") RecommendationRiskLevel severityLevel,
    @Column("automation_level") CdssAutomationLevel automationLevel,
    @Column("risk_level") RecommendationRiskLevel riskLevel,
    @Column("review_requirement") CdssReviewRequirement reviewRequirement,
    @Column("silent_run_hours") int silentRunHours,
    @Column("release_gate") String releaseGate,
    @Column("auto_execution_allowed") boolean autoExecutionAllowed,
    @Column("samd_classification") String samdClassification,
    @Column("regulatory_evidence") String regulatoryEvidence,
    CdssRiskMatrixStatus status,
    @Column("matrix_version") String matrixVersion,
    String explanation,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public CdssRiskAssessment toAssessment() {
        return new CdssRiskAssessment(
            matrixId, matrixVersion, riskLevel, reviewRequirement, silentRunHours, releaseGate,
            autoExecutionAllowed, samdClassification, regulatoryEvidence, explanation);
    }
}
