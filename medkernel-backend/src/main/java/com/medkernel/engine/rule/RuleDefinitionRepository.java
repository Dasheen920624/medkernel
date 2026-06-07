package com.medkernel.engine.rule;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则定义持久化仓库（GA-ENG-API-05）。
 *
 * <p>承担按租户 + 业务键查询、已发布列表加载与分页过滤计数，均强制带租户隔离。
 */
@Repository
public interface RuleDefinitionRepository extends ListCrudRepository<RuleDefinition, Long> {

    /**
     * 按规则业务 ID 与租户 ID 查询单条规则定义，用于详情、试运行、发布等单规则入口。
     */
    Optional<RuleDefinition> findByRuleIdAndTenantId(String ruleId, String tenantId);

    /**
     * 按租户和规则编码查询规则定义，用于创建前的同租户唯一性校验。
     */
    Optional<RuleDefinition> findByTenantIdAndRuleCode(String tenantId, String ruleCode);

    /**
     * 按租户查询内容已审核的规则定义，按更新时间倒序。
     *
     * <p>运行入口还必须校验对应统一资产版本为 {@code ACTIVE}，本查询不能单独证明规则可执行。
     */
    @Query("""
        SELECT * FROM rule_definition
        WHERE tenant_id = :tenantId AND status = 'PUBLISHED'
        ORDER BY updated_at DESC, id DESC
        """)
    List<RuleDefinition> findPublishedByTenantId(String tenantId);

    /**
     * 按状态、类型、风险级别可选过滤的规则定义分页查询；过滤条件为 {@code null} 时跳过。
     */
    @Query("""
        SELECT * FROM rule_definition
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:ruleType IS NULL OR rule_type = :ruleType)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RuleDefinition> pageByFilter(String tenantId, String status, String ruleType,
                                      String riskLevel, int offset, int limit);

    /**
     * 与分页查询同口径的完整列表，用于平台主源与当前租户覆盖层的有效资产合并。
     */
    @Query("""
        SELECT * FROM rule_definition
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:ruleType IS NULL OR rule_type = :ruleType)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
        ORDER BY updated_at DESC, id DESC
        """)
    List<RuleDefinition> listByFilter(String tenantId, String status, String ruleType, String riskLevel);

    /**
     * 与 {@link #pageByFilter} 同口径的总数查询，用于分页响应的 total 字段。
     */
    @Query("""
        SELECT COUNT(*) FROM rule_definition
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:ruleType IS NULL OR rule_type = :ruleType)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
        """)
    long countByFilter(String tenantId, String status, String ruleType, String riskLevel);
}
