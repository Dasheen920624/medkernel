package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准用药对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalMedicationRepository extends ListCrudRepository<ClinicalMedication, String> {

    List<ClinicalMedication> findByTenantId(String tenantId);

    List<ClinicalMedication> findByTenantIdAndPatientId(String tenantId, String patientId);
}
