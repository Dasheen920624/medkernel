package com.medkernel.engine.quality.dashboard;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

/**
 * 质量风险概览预警仓储。
 */
public interface QualityDashboardAlertRepository extends CrudRepository<QualityDashboardAlert, Long> {

    Optional<QualityDashboardAlert> findByTenantIdAndAlertTypeAndSourceTypeAndSourceId(
        String tenantId,
        QualityDashboardAlertType alertType,
        String sourceType,
        String sourceId);
}
