package com.medkernel.engine.versioning;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 通用资产版本仓库。
 */
@Repository
public interface AssetVersionRepository extends ListCrudRepository<AssetVersion, Long> {

    Optional<AssetVersion> findByVersionIdAndTenantId(String versionId, String tenantId);

    Optional<AssetVersion> findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String versionNo
    );

    List<AssetVersion> findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
        String tenantId,
        VersionedAssetType assetType,
        String activeScopeKey,
        AssetVersionStatus status
    );

    List<AssetVersion> findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        AssetVersionStatus status
    );
}
