package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import com.medkernel.engine.rule.RuleExecutionLog;

/**
 * 规则使用统计只读聚合仓库（DATASVC-01 PR1）。
 *
 * <p>从规则引擎运行事实 {@code rule_execution_log}（engine-rule 所属）**只读聚合**为 D2 去标识统计——
 * 不写该表（不违反域归属：仅写操作受 {@code DomainOwnershipContractTest} 约束）。跨方言用标准
 * {@code OFFSET ... ROWS FETCH NEXT} 分页与子查询计数。
 */
public interface RuleUsageStatsRepository extends Repository<RuleExecutionLog, Long> {

    /**
     * 按规则聚合时间窗口内执行事实为 D2 统计（执行总数倒序），服务端分页。
     */
    @Query("""
        SELECT rule_id AS rule_id,
               COUNT(*) AS total_executions,
               SUM(CASE WHEN hit = TRUE THEN 1 ELSE 0 END) AS hit_count,
               SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
               MAX(executed_at) AS last_executed_at
        FROM rule_execution_log
        WHERE tenant_id = :tenantId
          AND executed_at >= :windowStart
          AND executed_at < :windowEnd
        GROUP BY rule_id
        ORDER BY COUNT(*) DESC, rule_id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RuleUsageStat> aggregateRuleUsage(
        String tenantId, Instant windowStart, Instant windowEnd, int offset, int limit);

    /**
     * 统计时间窗口内规则分组总数（服务端分页 total）。
     */
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT rule_id
            FROM rule_execution_log
            WHERE tenant_id = :tenantId
              AND executed_at >= :windowStart
              AND executed_at < :windowEnd
            GROUP BY rule_id
        ) g
        """)
    long countRuleUsageGroups(String tenantId, Instant windowStart, Instant windowEnd);
}
