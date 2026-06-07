package com.medkernel.engine.rule;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则影子运行复核事实仓库。
 */
@Repository
public interface RuleShadowFeedbackRepository extends ListCrudRepository<RuleShadowFeedback, Long> {

    Optional<RuleShadowFeedback> findByTenantIdAndExecutionId(String tenantId, String executionId);

    long countByTenantIdAndRuleIdAndDecision(
        String tenantId, String ruleId, RuleShadowFeedbackDecision decision);
}
