package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准随访对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalFollowUpRepository extends ListCrudRepository<ClinicalFollowUp, String> {

    List<ClinicalFollowUp> findByTenantIdAndPatientId(String tenantId, String patientId);
}
