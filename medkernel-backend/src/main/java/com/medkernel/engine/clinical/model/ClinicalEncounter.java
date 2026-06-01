package com.medkernel.engine.clinical.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotBlank;

/**
 * SYS-01 标准就诊对象。
 */
@Table("mk_clinical_encounter")
public record ClinicalEncounter(
    @Id @Column("encounter_id") @NotBlank String encounterId,
    @Column("tenant_id") @NotBlank String tenantId,
    @Column("org_path") @NotBlank String orgPath,
    @Column("source_system") @NotBlank String sourceSystem,
    @Column("source_id") @NotBlank String sourceId,
    @Column("fhir_resource_id") String fhirResourceId,
    @Column("patient_id") @NotBlank String patientId,
    @Column("encounter_class") @NotBlank String encounterClass,
    @Column("status") @NotBlank String status,
    @Column("started_at") Instant startedAt,
    @Column("ended_at") Instant endedAt,
    @Column("org_unit_id") String orgUnitId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") @NotBlank String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") @NotBlank String updatedBy,
    @Column("trace_id") String traceId
) {}
