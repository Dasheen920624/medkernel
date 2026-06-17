package com.medkernel.engine.llm.eval;

import java.util.List;
import java.util.Optional;

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
     * AI 质量评测趋势查询：按能力码和模型版本取最近运行，服务层负责租户隔离。
     */
    List<ModelEvalRun> findTop20ByTenantIdAndCapabilityCodeAndModelVersionOrderByCreatedAtDesc(
        String tenantId, String capabilityCode, String modelVersion);
}
