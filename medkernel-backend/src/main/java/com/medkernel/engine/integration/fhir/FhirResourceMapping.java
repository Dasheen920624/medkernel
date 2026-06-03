package com.medkernel.engine.integration.fhir;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.context.CanonicalResourceType;

/**
 * FHIR 资源与标准临床资源的一一映射证据。
 */
@Table("mk_fhir_resource_mapping")
public record FhirResourceMapping(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("org_path") String orgPath,
    @Column("fhir_version") FhirVersion fhirVersion,
    @Column("fhir_resource_type") String fhirResourceType,
    @Column("fhir_id") String fhirId,
    @Column("canonical_resource_id") Long canonicalResourceId,
    @Column("canonical_resource_type") CanonicalResourceType canonicalResourceType,
    @Column("field_mapping_rate") BigDecimal fieldMappingRate,
    @Column("missing_field_count") Integer missingFieldCount,
    @Column("mapping_status") FhirMappingStatus mappingStatus,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
