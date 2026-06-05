package com.medkernel.engine.evaluation;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 评估运行持久化仓库（GA-ENG-API-08）。
 *
 * <p>按运行业务 ID 与租户 ID 装载一次评估运行，用于诊断响应和运行事实追溯。
 */
@Repository
public interface EvaluationRunRepository extends ListCrudRepository<EvaluationRun, Long> {

    /**
     * 按运行业务 ID 和租户 ID 查询评估运行事实。
     */
    Optional<EvaluationRun> findByRunIdAndTenantId(String runId, String tenantId);

    /**
     * 按稳定运行业务编码和租户 ID 查询评估运行事实，用于自动评估重放。
     */
    Optional<EvaluationRun> findByRunCodeAndTenantId(String runCode, String tenantId);
}
