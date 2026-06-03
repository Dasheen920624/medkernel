package com.medkernel.engine.integration.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 区域协同来源及可信分级档案。
 */
@Table("mk_integration_regional_source")
public record RegionalSource(
    @Id Long id,
    @Column("source_id") String sourceId,
    @Column("tenant_id") String tenantId,
    @Column("regional_network_name") String regionalNetworkName,
    @Column("source_organization_id") String sourceOrganizationId,
    @Column("source_organization_name") String sourceOrganizationName,
    @Column("trust_level") String trustLevel,
    @Column("evidence_text") String evidenceText,
    @Column("adapter_id") String adapterId,
    @Column("onboarding_id") String onboardingId,
    @Column("org_path") String orgPath,
    String status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
