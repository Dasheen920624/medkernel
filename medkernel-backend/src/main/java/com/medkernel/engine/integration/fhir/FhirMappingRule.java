package com.medkernel.engine.integration.fhir;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.context.CanonicalResourceType;

/**
 * 可版本化的 FHIR 字段到标准临床字段映射规则。
 */
@Table("mk_fhir_mapping_rule")
public record FhirMappingRule(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("rule_code") String ruleCode,
    @Column("fhir_version") FhirVersion fhirVersion,
    @Column("fhir_resource_type") String fhirResourceType,
    @Column("canonical_resource_type") CanonicalResourceType canonicalResourceType,
    @Column("fhir_path") String fhirPath,
    @Column("canonical_path") String canonicalPath,
    @Column("required_field") Boolean requiredField,
    @Column("transform_type") String transformType,
    @Column("rule_version") Integer ruleVersion,
    @Column("status") FhirMappingStatus status,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
