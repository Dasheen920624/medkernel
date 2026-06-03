package com.medkernel.engine.followup;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 随访计划实体。
 */
@Table("followup_plan")
public record FollowupPlan(
    @Id Long id,
    @Column("plan_id") String planId,
    @Column("tenant_id") String tenantId,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("pathway_id") String pathwayId,
    @Column("disease_code") String diseaseCode,
    @Column("risk_level") String riskLevel,
    FollowupPlanStatus status,
    @Column("idempotency_key") String idempotencyKey,
    @Column("source_fact_type") String sourceFactType,
    @Column("source_fact_id") String sourceFactId,
    @Column("generation_rule_code") String generationRuleCode,
    @Column("generation_explanation") String generationExplanation,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public FollowupPlan(
            Long id,
            String planId,
            String tenantId,
            String patientId,
            String encounterId,
            String pathwayId,
            String diseaseCode,
            String riskLevel,
            FollowupPlanStatus status,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, planId, tenantId, patientId, encounterId, pathwayId, diseaseCode, riskLevel, status, null,
            null, null, null, null,
            createdAt, createdBy, updatedAt, updatedBy, traceId);
    }

    public FollowupPlan(
            Long id,
            String planId,
            String tenantId,
            String patientId,
            String encounterId,
            String pathwayId,
            String diseaseCode,
            String riskLevel,
            FollowupPlanStatus status,
            String idempotencyKey,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, planId, tenantId, patientId, encounterId, pathwayId, diseaseCode, riskLevel, status, idempotencyKey,
            null, null, null, null,
            createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
