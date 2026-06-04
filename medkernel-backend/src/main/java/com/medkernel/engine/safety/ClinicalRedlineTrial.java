package com.medkernel.engine.safety;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * OPT-04 临床安全红线静默试运行证据。
 */
@Table("mk_engine_clinical_redline_trial")
public record ClinicalRedlineTrial(
    @Id Long id,
    @Column("trial_id") String trialId,
    @Column("tenant_id") String tenantId,
    @Column("redline_id") String redlineId,
    @Column("redline_key") String redlineKey,
    @Column("redline_version") String redlineVersion,
    ClinicalRedlineTrialStatus status,
    @Column("observed_from") Instant observedFrom,
    @Column("observed_to") Instant observedTo,
    @Column("required_silent_hours") long requiredSilentHours,
    @Column("actual_silent_hours") long actualSilentHours,
    @Column("evaluated_case_count") long evaluatedCaseCount,
    @Column("matched_case_count") long matchedCaseCount,
    @Column("false_positive_case_count") long falsePositiveCaseCount,
    @Column("safety_incident_count") long safetyIncidentCount,
    @Column("gate_passed") boolean gatePassed,
    @Column("evidence_reference") String evidenceReference,
    @Column("operator_note") String operatorNote,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
    public ClinicalRedlineTrialResponse toResponse() {
        return new ClinicalRedlineTrialResponse(
            trialId,
            redlineId,
            redlineKey,
            redlineVersion,
            status,
            observedFrom,
            observedTo,
            requiredSilentHours,
            actualSilentHours,
            evaluatedCaseCount,
            matchedCaseCount,
            falsePositiveCaseCount,
            safetyIncidentCount,
            gatePassed,
            evidenceReference,
            traceId);
    }
}
