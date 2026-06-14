package com.medkernel.engine.llm.provider;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 模型 provider 配置数据访问存储库（LLM-08）。
 */
@Repository
public interface ModelProviderConfigRepository extends CrudRepository<ModelProviderConfig, Long> {

    Optional<ModelProviderConfig> findByTenantIdAndProviderCode(String tenantId, String providerCode);

    List<ModelProviderConfig> findByTenantIdAndEnabledFlag(String tenantId, String enabledFlag);
}
