package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准照护计划对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalCarePlanRepository extends ListCrudRepository<ClinicalCarePlan, String> {

    List<ClinicalCarePlan> findByTenantId(String tenantId);

    List<ClinicalCarePlan> findByTenantIdAndPatientId(String tenantId, String patientId);
}
