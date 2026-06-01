package com.medkernel.engine.clinical.model;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准诊断报告对象仓储，只暴露租户作用域查询。
 */
@Repository
public interface ClinicalDiagnosticReportRepository extends ListCrudRepository<ClinicalDiagnosticReport, String> {

    List<ClinicalDiagnosticReport> findByTenantId(String tenantId);

    List<ClinicalDiagnosticReport> findByTenantIdAndPatientId(String tenantId, String patientId);
}
