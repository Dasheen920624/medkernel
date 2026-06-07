package com.medkernel.engine.rule;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则版本适用域检索镜像仓库。
 */
@Repository
public interface RuleApplicabilityRepository extends ListCrudRepository<RuleApplicability, Long> {

    /**
     * 按租户和规则版本查询唯一适用域镜像。
     */
    Optional<RuleApplicability> findByTenantIdAndRuleVersionId(String tenantId, String ruleVersionId);
}
