package com.medkernel.engine.quality.dashboard;

import org.springframework.data.repository.CrudRepository;

/**
 * 质控驾驶舱预警仓储。
 */
public interface QualityDashboardAlertRepository extends CrudRepository<QualityDashboardAlert, Long> {
}
