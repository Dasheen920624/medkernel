package com.medkernel.engine.recommendation;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 疲劳治理信号持久化仓库，提供按卡/触发关联查询及按 fatigueKey / 信号类型分页。
 */
@Repository
public interface RecommendationFatigueSignalRepository extends ListCrudRepository<RecommendationFatigueSignal, Long> {

    List<RecommendationFatigueSignal> findByCardIdAndTenantIdOrderByCreatedAtAsc(String cardId, String tenantId);

    List<RecommendationFatigueSignal> findByTriggerIdAndTenantIdOrderByCreatedAtAsc(String triggerId, String tenantId);

    /** 按 fatigueKey 与信号类型过滤当前租户的疲劳治理信号，返回 limit+1 条用于估算游标分页。 */
    @Query("""
        SELECT *
        FROM recommendation_fatigue_signal
        WHERE tenant_id = :tenantId
          AND (:fatigueKey IS NULL OR fatigue_key = :fatigueKey)
          AND (:signalType IS NULL OR signal_type = :signalType)
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<RecommendationFatigueSignal> pageByFilter(String tenantId, String fatigueKey, String signalType,
                                                   int offset, int limit);

    /** 统计疲劳窗口内同患者同 fatigueKey 的低价值反馈信号数量，用于 explainable suppression。 */
    @Query("""
        SELECT COALESCE(SUM(occurrence_count), 0)
        FROM recommendation_fatigue_signal
        WHERE tenant_id = :tenantId
          AND patient_id = :patientId
          AND fatigue_key = :fatigueKey
          AND signal_type IN ('REJECTED', 'DISMISSED')
          AND created_at >= :windowStartedAt
        """)
    long countLowValueSignals(String tenantId, String patientId, String fatigueKey, Instant windowStartedAt);
}
