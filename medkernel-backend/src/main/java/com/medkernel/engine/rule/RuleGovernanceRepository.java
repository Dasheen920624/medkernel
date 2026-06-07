package com.medkernel.engine.rule;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

/**
 * 规则治理事实仓库。
 */
public interface RuleGovernanceRepository extends CrudRepository<RuleGovernance, Long> {

    Optional<RuleGovernance> findByRuleVersionIdAndTenantId(String ruleVersionId, String tenantId);
}
