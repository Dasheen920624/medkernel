package com.medkernel.engine.clinical.model;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import jakarta.validation.constraints.NotBlank;

/**
 * SYS-01 标准患者对象，敏感字段只保存密文与掩码。
 */
@Table("mk_clinical_patient")
public record ClinicalPatient(
    @Id @Column("patient_id") @NotBlank String patientId,
    @Column("tenant_id") @NotBlank String tenantId,
    @Column("org_path") @NotBlank String orgPath,
    @Column("source_system") @NotBlank String sourceSystem,
    @Column("source_id") @NotBlank String sourceId,
    @Column("fhir_resource_id") String fhirResourceId,
    @Column("name_cipher") @NotBlank String nameCipher,
    @Column("name_mask") @NotBlank String nameMask,
    @Column("identity_no_cipher") String identityNoCipher,
    @Column("identity_no_mask") String identityNoMask,
    @Column("phone_cipher") String phoneCipher,
    @Column("phone_mask") String phoneMask,
    @Column("birth_date") LocalDate birthDate,
    @Column("gender_code") String genderCode,
    @Column("created_at") Instant createdAt,
    @Column("created_by") @NotBlank String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") @NotBlank String updatedBy,
    @Column("trace_id") String traceId
) {}
