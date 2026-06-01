package com.medkernel.engine.clinical.model;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准患者对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalPatientRepository extends ListCrudRepository<ClinicalPatient, String> {

    List<ClinicalPatient> findByTenantId(String tenantId);

    Optional<ClinicalPatient> findByTenantIdAndPatientId(String tenantId, String patientId);

    Optional<ClinicalPatient> findByTenantIdAndSourceSystemAndSourceId(
        String tenantId, String sourceSystem, String sourceId);
}
