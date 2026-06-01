package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准护理评估对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalNursingAssessmentRepository extends ListCrudRepository<ClinicalNursingAssessment, String> {

    List<ClinicalNursingAssessment> findByTenantIdAndPatientId(String tenantId, String patientId);
}
