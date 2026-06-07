package com.medkernel.engine.rule;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

/**
 * 规则治理签署事实仓库。
 */
public interface RuleSignoffRepository extends CrudRepository<RuleSignoff, Long> {

    List<RuleSignoff> findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
        String ruleVersionId,
        String tenantId
    );

    boolean existsByRuleVersionIdAndTenantIdAndStageAndReviewRoundAndSignerId(
        String ruleVersionId,
        String tenantId,
        RuleSignoffStage stage,
        int reviewRound,
        String signerId
    );
}
