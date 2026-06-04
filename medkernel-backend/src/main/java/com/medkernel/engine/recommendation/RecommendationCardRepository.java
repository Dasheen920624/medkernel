package com.medkernel.engine.recommendation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 推荐卡持久化仓库，提供卡 id 查询、触发关联查询和按筛选条件分页/计数。
 */
@Repository
public interface RecommendationCardRepository extends ListCrudRepository<RecommendationCard, Long> {

    Optional<RecommendationCard> findByCardIdAndTenantId(String cardId, String tenantId);

    List<RecommendationCard> findByTriggerIdAndTenantIdOrderByCreatedAtAsc(String triggerId, String tenantId);

    /** 按状态/风险/场景/患者/就诊/触发点过滤当前租户的推荐卡总数。 */
    @Query("""
        SELECT COUNT(*)
        FROM recommendation_card c
        JOIN recommendation_trigger t ON t.trigger_id = c.trigger_id AND t.tenant_id = c.tenant_id
        WHERE c.tenant_id = :tenantId
          AND (:status IS NULL OR c.status = :status)
          AND (:riskLevel IS NULL OR c.risk_level = :riskLevel)
          AND (:scenarioCode IS NULL OR t.scenario_code = :scenarioCode)
          AND (:patientId IS NULL OR t.patient_id = :patientId)
          AND (:encounterId IS NULL OR t.encounter_id = :encounterId)
          AND (:triggerPoint IS NULL OR t.trigger_type = :triggerPoint)
        """)
    long countByFilter(String tenantId, String status, String riskLevel, String scenarioCode, String patientId,
                       String encounterId, String triggerPoint);

    /** 按状态/风险/场景/患者/就诊/触发点过滤分页返回推荐卡，默认按 created_at 倒序。 */
    @Query("""
        SELECT c.*
        FROM recommendation_card c
        JOIN recommendation_trigger t ON t.trigger_id = c.trigger_id AND t.tenant_id = c.tenant_id
        WHERE c.tenant_id = :tenantId
          AND (:status IS NULL OR c.status = :status)
          AND (:riskLevel IS NULL OR c.risk_level = :riskLevel)
          AND (:scenarioCode IS NULL OR t.scenario_code = :scenarioCode)
          AND (:patientId IS NULL OR t.patient_id = :patientId)
          AND (:encounterId IS NULL OR t.encounter_id = :encounterId)
          AND (:triggerPoint IS NULL OR t.trigger_type = :triggerPoint)
        ORDER BY c.created_at DESC, c.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RecommendationCard> pageByFilter(
        String tenantId, String status, String riskLevel, String scenarioCode, String patientId,
        String encounterId, String triggerPoint, int offset, int limit);
}
