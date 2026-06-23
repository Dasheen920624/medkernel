package com.medkernel.engine.context;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 租户自定义上下文字段存储库。多租户隔离，仅按 tenant_id 查询。
 */
@Repository
public interface ContextFieldCatalogRepository
    extends ListCrudRepository<ContextFieldCatalogEntry, Long> {

    List<ContextFieldCatalogEntry> findAllByTenantIdAndStatus(String tenantId, String status);

    Optional<ContextFieldCatalogEntry> findByTenantIdAndFieldPath(String tenantId, String fieldPath);

    Optional<ContextFieldCatalogEntry> findByTenantIdAndFieldId(String tenantId, String fieldId);
}
