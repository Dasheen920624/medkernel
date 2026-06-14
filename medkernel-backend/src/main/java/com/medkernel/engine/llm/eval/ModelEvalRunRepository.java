package com.medkernel.engine.llm.eval;

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
}
