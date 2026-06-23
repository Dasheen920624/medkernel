package com.medkernel.engine.versioning;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 统一配置资产正文仓库。
 */
@Repository
public interface AssetVersionContentRepository extends ListCrudRepository<AssetVersionContent, Long> {

    Optional<AssetVersionContent> findByTenantIdAndVersionId(String tenantId, String versionId);
}
