package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准医保结算对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalClaimRepository extends ListCrudRepository<ClinicalClaim, String> {

    List<ClinicalClaim> findByTenantIdAndPatientId(String tenantId, String patientId);
}
