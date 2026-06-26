package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import com.medkernel.engine.recommendation.RecommendationCard;

/**
 * 临床信号统计只读聚合仓库（DATASVC-01）。
 *
 * <p>从推荐引擎真实投递的 CDSS 决策信号事实 {@code recommendation_card}（engine-recommendation 所属）
 * **只读聚合**为 D2 去标识统计——不写该表（不违反域归属：仅写操作受 {@code DomainOwnershipContractTest} 约束）。
 * 仅聚合卡级计量字段（类别/风险/状态/时刻），不触碰患者字段（card 表无患者标识，D2）。跨方言用标准
 * {@code OFFSET ... ROWS FETCH NEXT} 分页与子查询计数。
 */
public interface ClinicalSignalsRepository extends Repository<RecommendationCard, Long> {

    /**
     * 按信号类别聚合时间窗口内 CDSS 信号事实为 D2 统计（信号总数倒序），服务端分页。
     * {@code acceptedCount}/{@code rejectedCount} 为真实记录状态的计数，非伪造采纳率（铁律 #1）。
     */
    @Query("""
        SELECT card_type AS signal_type,
               COUNT(*) AS total_signals,
               SUM(CASE WHEN risk_level IN ('HIGH', 'CRITICAL') THEN 1 ELSE 0 END) AS high_risk_count,
               SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted_count,
               SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
               MAX(created_at) AS last_signal_at
        FROM recommendation_card
        WHERE tenant_id = :tenantId
          AND created_at >= :windowStart
          AND created_at < :windowEnd
        GROUP BY card_type
        ORDER BY COUNT(*) DESC, card_type ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ClinicalSignalStat> aggregateClinicalSignals(
        String tenantId, Instant windowStart, Instant windowEnd, int offset, int limit);

    /**
     * 统计时间窗口内信号类别分组总数（服务端分页 total）。
     */
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT card_type
            FROM recommendation_card
            WHERE tenant_id = :tenantId
              AND created_at >= :windowStart
              AND created_at < :windowEnd
            GROUP BY card_type
        ) g
        """)
    long countClinicalSignalGroups(String tenantId, Instant windowStart, Instant windowEnd);
}
