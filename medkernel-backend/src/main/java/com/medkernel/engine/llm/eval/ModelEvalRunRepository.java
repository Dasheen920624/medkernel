package com.medkernel.engine.llm.eval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 模型评测运行结果数据访问存储库（LLM-07）。
 */
@Repository
public interface ModelEvalRunRepository extends CrudRepository<ModelEvalRun, Long> {

    /**
     * 上线门禁查询：指定 provider + 模型版本是否存在指定状态的最近一条评测运行。
     */
    Optional<ModelEvalRun> findFirstByTenantIdAndProviderCodeAndModelVersionAndStatusOrderByIdDesc(
        String tenantId, String providerCode, String modelVersion, String status);

    /**
     * 知识生产能力门禁查询：评测能力码必须与本次生产能力完全一致。
     */
    Optional<ModelEvalRun>
        findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
            String tenantId,
            String providerCode,
            String modelVersion,
            String capabilityCode,
            String status);

    /**
     * 专家签字一次性状态跃迁：并发请求只有一个能把待复核改为通过，禁止覆盖既有审核人。
     */
    @Modifying
    @Query("""
        UPDATE mk_llm_eval_run
           SET status = 'PASSED',
               reviewer = :reviewer,
               signed_at = :signedAt,
               updated_at = :signedAt,
               updated_by = :reviewer
         WHERE id = :id
           AND tenant_id = :tenantId
           AND status = 'PENDING_REVIEW'
           AND reviewer IS NULL
           AND signed_at IS NULL
        """)
    int signOffPending(Long id, String tenantId, String reviewer, Instant signedAt);

    /**
     * AI 质量评测趋势查询：按能力码和模型版本取最近运行，服务层负责租户隔离。
     */
    List<ModelEvalRun> findTop20ByTenantIdAndCapabilityCodeAndModelVersionOrderByCreatedAtDesc(
        String tenantId, String capabilityCode, String modelVersion);
}
