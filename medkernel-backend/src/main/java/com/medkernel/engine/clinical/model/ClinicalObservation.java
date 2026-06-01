package com.medkernel.engine.clinical.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotBlank;

/**
 * SYS-01 标准观察对象，覆盖检验、体征和量表事实。
 */
@Table("mk_clinical_observation")
public record ClinicalObservation(
    @Id @Column("observation_id") @NotBlank String observationId,
    @Column("tenant_id") @NotBlank String tenantId,
    @Column("org_path") @NotBlank String orgPath,
    @Column("source_system") @NotBlank String sourceSystem,
    @Column("source_id") @NotBlank String sourceId,
    @Column("fhir_resource_id") String fhirResourceId,
    @Column("patient_id") @NotBlank String patientId,
    @Column("encounter_id") String encounterId,
    @Column("code") @NotBlank String code,
    @Column("code_system") @NotBlank String codeSystem,
    @Column("display_name") @NotBlank String displayName,
    @Column("value_numeric") BigDecimal valueNumeric,
    @Column("unit") String unit,
    @Column("critical_flag") String criticalFlag,
    @Column("created_at") Instant createdAt,
    @Column("created_by") @NotBlank String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") @NotBlank String updatedBy,
    @Column("trace_id") String traceId
) {}
