package com.medkernel.engine.rule;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则人工越权事实仓库。
 */
@Repository
public interface RuleOverrideLogRepository extends ListCrudRepository<RuleOverrideLog, Long> {

    /**
     * 查询同一次执行同一动作的既有越权事实，阻止重复提交。
     */
    Optional<RuleOverrideLog> findByTenantIdAndExecutionIdAndActionCode(
        String tenantId, String executionId, RuleActionCode actionCode);
}
