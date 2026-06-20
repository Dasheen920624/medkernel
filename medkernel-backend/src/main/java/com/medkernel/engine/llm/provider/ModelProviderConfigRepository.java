package com.medkernel.engine.llm.provider;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 模型 provider 配置数据访问存储库（LLM-08）。
 */
@Repository
public interface ModelProviderConfigRepository extends CrudRepository<ModelProviderConfig, Long> {

    Optional<ModelProviderConfig> findByTenantIdAndProviderCode(String tenantId, String providerCode);

    List<ModelProviderConfig> findByTenantIdAndEnabledFlag(String tenantId, String enabledFlag);

    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_llm_provider
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ModelProviderConfig> pageByTenantId(String tenantId, int offset, int limit);
}
