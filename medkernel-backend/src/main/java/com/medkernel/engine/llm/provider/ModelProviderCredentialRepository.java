package com.medkernel.engine.llm.provider;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 模型服务机构凭据仓储。任何读取和变更必须同时携带服务机构与模型服务编码。
 */
@Repository
public interface ModelProviderCredentialRepository
        extends ListCrudRepository<ModelProviderCredential, Long> {

    Optional<ModelProviderCredential> findByTenantIdAndProviderCode(
        String tenantId,
        String providerCode
    );
}
