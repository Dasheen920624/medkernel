package com.medkernel.engine.versioning;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 资产依赖图仓库。
 */
@Repository
public interface AssetDependencyRepository extends ListCrudRepository<AssetDependency, Long> {

    List<AssetDependency> findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String versionId
    );

    List<AssetDependency> findByTenantIdAndDependsOnAssetTypeAndDependsOnIdentity(
        String tenantId,
        VersionedAssetType dependsOnAssetType,
        String dependsOnIdentity
    );

    void deleteByTenantIdAndAssetTypeAndAssetIdentityAndVersionId(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String versionId
    );
}
