package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * 高危近似术语规则仓库。
 */
public interface HighRiskRuleRepository extends ListCrudRepository<HighRiskRule, Long> {

    Optional<HighRiskRule> findByTenantIdAndRuleCode(String tenantId, String ruleCode);

    /**
     * 查询当前租户可用规则：租户覆盖规则优先，SYSTEM 规则作为全局安全底线。
     */
    @Query("""
        SELECT *
        FROM mk_term_high_risk_rule
        WHERE status = 'ACTIVE'
          AND (tenant_id = 'SYSTEM' OR tenant_id = :tenantId)
          AND (:category IS NULL OR category IS NULL OR category = :category)
        ORDER BY CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END, id
        """)
    List<HighRiskRule> findActiveByTenantIdAndCategory(String tenantId, TermCategory category);
}
