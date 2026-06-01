package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准手术与操作对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalProcedureRepository extends ListCrudRepository<ClinicalProcedure, String> {

    List<ClinicalProcedure> findByTenantIdAndPatientId(String tenantId, String patientId);
}
