package com.medkernel.engine.integration.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 第三方业务接口接入生命周期档案。
 */
@Table("mk_integration_onboarding")
public record IntegrationOnboarding(
    @Id Long id,
    @Column("onboarding_id") String onboardingId,
    @Column("tenant_id") String tenantId,
    String name,
    @Column("access_mode") String accessMode,
    @Column("adapter_id") String adapterId,
    @Column("fhir_version") String fhirVersion,
    @Column("source_system") String sourceSystem,
    @Column("business_scenario") String businessScenario,
    @Column("org_path") String orgPath,
    @Column("callback_webhook_id") String callbackWebhookId,
    String status,
    @Column("evidence_text") String evidenceText,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public IntegrationOnboarding withStatus(String newStatus, String newEvidenceText, String updatedBy) {
        return new IntegrationOnboarding(
            id,
            onboardingId,
            tenantId,
            name,
            accessMode,
            adapterId,
            fhirVersion,
            sourceSystem,
            businessScenario,
            orgPath,
            callbackWebhookId,
            newStatus,
            newEvidenceText,
            createdAt,
            createdBy,
            Instant.now(),
            updatedBy,
            traceId
        );
    }
}
