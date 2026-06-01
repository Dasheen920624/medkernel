package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准临床文书对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalDocumentRepository extends ListCrudRepository<ClinicalDocument, String> {

    List<ClinicalDocument> findByTenantIdAndPatientId(String tenantId, String patientId);
}
