package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准医保结算对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalClaimRepository extends ListCrudRepository<ClinicalClaim, String> {

    default void insert(ClinicalClaim claim) {
        insert(
            claim.claimId(),
            claim.tenantId(),
            claim.orgPath(),
            claim.sourceSystem(),
            claim.sourceId(),
            claim.fhirResourceId(),
            claim.patientId(),
            claim.encounterId(),
            claim.claimType(),
            claim.status(),
            claim.totalAmount(),
            claim.createdAt(),
            claim.createdBy(),
            claim.updatedAt(),
            claim.updatedBy(),
            claim.traceId()
        );
    }

    @Modifying
    @Query("""
        INSERT INTO mk_clinical_claim (
            claim_id, tenant_id, org_path, source_system, source_id, fhir_resource_id,
            patient_id, encounter_id, claim_type, status, total_amount,
            created_at, created_by, updated_at, updated_by, trace_id
        ) VALUES (
            :claimId, :tenantId, :orgPath, :sourceSystem, :sourceId, :fhirResourceId,
            :patientId, :encounterId, :claimType, :status, :totalAmount,
            :createdAt, :createdBy, :updatedAt, :updatedBy, :traceId
        )
        """)
    void insert(String claimId, String tenantId, String orgPath, String sourceSystem, String sourceId,
                String fhirResourceId, String patientId, String encounterId, String claimType, String status,
                java.math.BigDecimal totalAmount, java.time.Instant createdAt, String createdBy,
                java.time.Instant updatedAt, String updatedBy, String traceId);

    List<ClinicalClaim> findByTenantId(String tenantId);

    List<ClinicalClaim> findByTenantIdAndPatientId(String tenantId, String patientId);
}
