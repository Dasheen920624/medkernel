package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准就诊对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalEncounterRepository extends ListCrudRepository<ClinicalEncounter, String> {

    List<ClinicalEncounter> findByTenantId(String tenantId);

    List<ClinicalEncounter> findByTenantIdAndPatientId(String tenantId, String patientId);
}
