package com.medkernel.engine.rule;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则历史回测事实仓库。
 */
@Repository
public interface RuleBacktestRunRepository extends ListCrudRepository<RuleBacktestRun, Long> {

    Optional<RuleBacktestRun> findByTenantIdAndBacktestId(String tenantId, String backtestId);

    @Query("""
        SELECT * FROM rule_backtest_run
        WHERE tenant_id = :tenantId AND rule_id = :ruleId
        ORDER BY created_at DESC, id DESC
        FETCH NEXT 1 ROWS ONLY
        """)
    Optional<RuleBacktestRun> findLatestByTenantIdAndRuleId(String tenantId, String ruleId);
}
