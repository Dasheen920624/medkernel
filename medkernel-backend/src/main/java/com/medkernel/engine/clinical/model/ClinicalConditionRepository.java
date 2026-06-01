package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准诊断对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalConditionRepository extends ListCrudRepository<ClinicalCondition, String> {

    List<ClinicalCondition> findByTenantIdAndPatientId(String tenantId, String patientId);
}
