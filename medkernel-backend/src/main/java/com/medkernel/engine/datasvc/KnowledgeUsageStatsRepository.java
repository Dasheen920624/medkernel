package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import com.medkernel.engine.recommendation.RecommendationSource;

/**
 * 知识使用统计只读聚合仓库（DATASVC-01 PR1）。
 *
 * <p>从推荐卡来源解释事实 {@code recommendation_source}（engine-recommendation 所属）中
 * {@code source_type='KNOWLEDGE'} 子集**只读聚合**为 D2 去标识统计——不写该表（不违反域归属：仅写操作受
 * {@code DomainOwnershipContractTest} 约束）。仅计入 {@code source_ref_id} 非空者（缺稳定知识键无法成统计）。
 * 跨方言用标准 {@code OFFSET ... ROWS FETCH NEXT} 分页与子查询计数。
 */
public interface KnowledgeUsageStatsRepository extends Repository<RecommendationSource, Long> {

    /**
     * 按知识引用键聚合时间窗口内被引用事实为 D2 统计（引用次数倒序），服务端分页。
     */
    @Query("""
        SELECT source_ref_id AS knowledge_ref_id,
               MAX(source_title) AS knowledge_title,
               COUNT(*) AS citation_count,
               COUNT(DISTINCT card_id) AS distinct_card_count,
               MAX(created_at) AS last_used_at
        FROM recommendation_source
        WHERE tenant_id = :tenantId
          AND source_type = 'KNOWLEDGE'
          AND source_ref_id IS NOT NULL
          AND created_at >= :windowStart
          AND created_at < :windowEnd
        GROUP BY source_ref_id
        ORDER BY COUNT(*) DESC, source_ref_id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeUsageStat> aggregateKnowledgeUsage(
        String tenantId, Instant windowStart, Instant windowEnd, int offset, int limit);

    /**
     * 统计时间窗口内知识引用分组总数（服务端分页 total）。
     */
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT source_ref_id
            FROM recommendation_source
            WHERE tenant_id = :tenantId
              AND source_type = 'KNOWLEDGE'
              AND source_ref_id IS NOT NULL
              AND created_at >= :windowStart
              AND created_at < :windowEnd
            GROUP BY source_ref_id
        ) g
        """)
    long countKnowledgeUsageGroups(String tenantId, Instant windowStart, Instant windowEnd);
}
