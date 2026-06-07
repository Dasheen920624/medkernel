package com.medkernel.engine.rule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则执行日志持久化仓库（GA-ENG-API-05）。
 *
 * <p>按 {@code execution_id} 装载单次执行用于诊断，按 {@code rule_id} 分页拉取命中历史。
 */
@Repository
public interface RuleExecutionLogRepository extends ListCrudRepository<RuleExecutionLog, Long> {

    /**
     * 按执行业务 ID 与租户 ID 查询单次规则执行日志，用于可解释诊断响应装配。
     */
    Optional<RuleExecutionLog> findByExecutionIdAndTenantId(String executionId, String tenantId);

    /**
     * 统计当前租户的规则执行日志总数。
     */
    @Query("SELECT COUNT(*) FROM rule_execution_log WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);

    /**
     * 统计某规则影子运行执行总数。
     */
    @Query("""
        SELECT COUNT(*) FROM rule_execution_log
        WHERE tenant_id = :tenantId
          AND rule_id = :ruleId
          AND status = 'SHADOW_RECORDED'
        """)
    long countShadowByRule(String tenantId, String ruleId);

    /**
     * 按命中结果统计某规则影子运行执行数。
     */
    @Query("""
        SELECT COUNT(*) FROM rule_execution_log
        WHERE tenant_id = :tenantId
          AND rule_id = :ruleId
          AND status = 'SHADOW_RECORDED'
          AND hit = :hit
        """)
    long countShadowByRuleAndHit(String tenantId, String ruleId, boolean hit);

    /**
     * 按执行时间倒序分页读取当前租户的规则执行日志。
     */
    @Query("""
        SELECT * FROM rule_execution_log
        WHERE tenant_id = :tenantId
        ORDER BY executed_at DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RuleExecutionLog> pageByTenantId(String tenantId, int offset, int limit);

    /**
     * 按规则倒序分页查询执行日志，按 {@code executed_at} 倒序，配合命中统计与回放。
     */
    @Query("""
        SELECT * FROM rule_execution_log
        WHERE tenant_id = :tenantId AND rule_id = :ruleId
        ORDER BY executed_at DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RuleExecutionLog> pageByRule(String tenantId, String ruleId, int offset, int limit);

    /**
     * 查询同患者同语义动作窗口内最近一次真实产出，用于跨事件去重。
     */
    @Query("""
        SELECT * FROM rule_execution_log
        WHERE tenant_id = :tenantId
          AND patient_id = :patientId
          AND semantic_key = :semanticKey
          AND status = 'SUCCESS'
          AND executed_at >= :cutoff
        ORDER BY executed_at DESC
        FETCH NEXT 1 ROWS ONLY
        """)
    Optional<RuleExecutionLog> findRecentSuccessful(
        String tenantId, String patientId, String semanticKey, Instant cutoff);
}
