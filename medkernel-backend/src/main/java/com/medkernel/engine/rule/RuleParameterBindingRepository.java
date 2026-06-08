package com.medkernel.engine.rule;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则参数绑定持久化仓库。
 *
 * <p>按规则版本与租户读取参数值，用于参数化规则审计、复用与后续批量生成结果核验。
 */
@Repository
public interface RuleParameterBindingRepository extends ListCrudRepository<RuleParameterBinding, Long> {

    /**
     * 按规则版本列出参数值，参数键排序保证审计展示稳定。
     */
    @Query("""
        SELECT * FROM mk_engine_rule_parameter_binding
        WHERE rule_version_id = :ruleVersionId AND tenant_id = :tenantId
        ORDER BY param_key ASC
        """)
    List<RuleParameterBinding> findByRuleVersionIdAndTenantIdOrderByParamKeyAsc(
        String ruleVersionId,
        String tenantId
    );
}
