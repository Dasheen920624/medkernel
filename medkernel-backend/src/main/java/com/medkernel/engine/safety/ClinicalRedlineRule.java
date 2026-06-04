package com.medkernel.engine.safety;

import java.time.Instant;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * OPT-04 临床安全红线规则版本。
 */
@Table("mk_engine_clinical_redline")
public record ClinicalRedlineRule(
    @Id Long id,
    @Column("redline_id") String redlineId,
    @Column("tenant_id") String tenantId,
    ClinicalRedlineCategory category,
    @Column("trigger_point") String triggerPoint,
    @Column("scope_type") String scopeType,
    @Column("scope_ref") String scopeRef,
    @Column("active_scope_key") String activeScopeKey,
    @Column("redline_key") String redlineKey,
    @Column("redline_version") String redlineVersion,
    ClinicalRedlineStatus status,
    @Column("hazard_severity") RecommendationRiskLevel hazardSeverity,
    @Column("risk_matrix_id") String riskMatrixId,
    @Column("risk_matrix_version") String riskMatrixVersion,
    @Column("review_requirement") CdssReviewRequirement reviewRequirement,
    @Column("silent_run_hours") int silentRunHours,
    @Column("release_gate") String releaseGate,
    String title,
    @Column("clinical_hazard") String clinicalHazard,
    @Column("condition_dsl") String conditionDsl,
    @Column("evidence_source") String evidenceSource,
    @Column("evidence_reference") String evidenceReference,
    @Column("source_version_id") Long sourceVersionId,
    @Column("lower_tenant_override_allowed") boolean lowerTenantOverrideAllowed,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public ClinicalRedlineRule withStatus(
            ClinicalRedlineStatus nextStatus,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        return new ClinicalRedlineRule(
            id,
            redlineId,
            tenantId,
            category,
            triggerPoint,
            scopeType,
            scopeRef,
            nextStatus == ClinicalRedlineStatus.ACTIVE ? activeScopeKey() : null,
            redlineKey,
            redlineVersion,
            nextStatus,
            hazardSeverity,
            riskMatrixId,
            riskMatrixVersion,
            reviewRequirement,
            silentRunHours,
            releaseGate,
            title,
            clinicalHazard,
            conditionDsl,
            evidenceSource,
            evidenceReference,
            sourceVersionId,
            lowerTenantOverrideAllowed,
            createdAt,
            createdBy,
            updatedAt,
            updatedBy,
            traceId);
    }

    public String activeScopeKey() {
        return tenantId + "|" + category.name() + "|" + triggerPoint + "|" + redlineKey;
    }

    public ClinicalRedlineResponse toResponse() {
        return new ClinicalRedlineResponse(
            redlineId,
            category,
            redlineKey,
            redlineVersion,
            status,
            title,
            clinicalHazard,
            conditionDsl,
            hazardSeverity,
            riskMatrixId,
            riskMatrixVersion,
            reviewRequirement,
            silentRunHours,
            releaseGate,
            evidenceSource,
            evidenceReference,
            sourceVersionId,
            lowerTenantOverrideAllowed);
    }
}
