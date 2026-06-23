package com.medkernel.engine.cdss.risk;

import java.util.List;
import java.util.Optional;

import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * CDSS 风险矩阵持久化仓库。
 */
@Repository
public interface CdssRiskMatrixRepository extends ListCrudRepository<CdssRiskMatrixRule, Long> {

    List<CdssRiskMatrixRule>
        findByTenantIdAndTriggerPointAndSeverityLevelAndAutomationLevelAndStatusOrderByUpdatedAtDescIdDesc(
            String tenantId,
            String triggerPoint,
            RecommendationRiskLevel severityLevel,
            CdssAutomationLevel automationLevel,
            CdssRiskMatrixStatus status);

    List<CdssRiskMatrixRule> findByTenantIdAndStatusOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
        String tenantId,
        CdssRiskMatrixStatus status);

    List<CdssRiskMatrixRule> findByTenantIdAndMatrixVersionOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
        String tenantId,
        String matrixVersion);

    default Optional<CdssRiskMatrixRule> findActiveRule(
            String tenantId,
            String triggerPoint,
            RecommendationRiskLevel severityLevel,
            CdssAutomationLevel automationLevel) {
        return findByTenantIdAndTriggerPointAndSeverityLevelAndAutomationLevelAndStatusOrderByUpdatedAtDescIdDesc(
            tenantId, triggerPoint, severityLevel, automationLevel, CdssRiskMatrixStatus.ACTIVE)
            .stream()
            .findFirst();
    }
}
