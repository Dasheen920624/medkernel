package com.medkernel.engine.knowledge.material;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 文档原件资料库对象账本仓储。所有查找必须带 tenantId，防止跨租户取回原件。
 */
@Repository
public interface KnowledgeMaterialObjectRepository extends ListCrudRepository<KnowledgeMaterialObject, Long> {

    Optional<KnowledgeMaterialObject> findByTenantIdAndId(String tenantId, Long id);

    Optional<KnowledgeMaterialObject> findByTenantIdAndFileUri(String tenantId, String fileUri);

    Optional<KnowledgeMaterialObject> findByTenantIdAndScopeKeyAndSha256(
        String tenantId,
        String scopeKey,
        String sha256);
}
