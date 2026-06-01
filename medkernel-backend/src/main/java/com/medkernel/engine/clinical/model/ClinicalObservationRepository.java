package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准观察对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalObservationRepository extends ListCrudRepository<ClinicalObservation, String> {

    List<ClinicalObservation> findByTenantId(String tenantId);

    List<ClinicalObservation> findByTenantIdAndPatientId(String tenantId, String patientId);
}
