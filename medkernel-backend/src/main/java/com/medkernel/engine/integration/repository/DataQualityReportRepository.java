package com.medkernel.engine.integration.repository;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import com.medkernel.engine.integration.domain.DataQualityReport;

/**
 * 数据质量报告快照仓储。
 */
@Repository
public interface DataQualityReportRepository extends ListCrudRepository<DataQualityReport, String> {
}
