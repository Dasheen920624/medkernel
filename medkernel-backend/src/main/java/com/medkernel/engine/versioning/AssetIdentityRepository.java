package com.medkernel.engine.versioning;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 稳定资产身份仓储。
 */
@Repository
public interface AssetIdentityRepository extends ListCrudRepository<AssetIdentity, Long> {

    Optional<AssetIdentity> findByTenantIdAndAssetTypeAndAssetIdentity(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity
    );

    List<AssetIdentity> findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(String tenantId);
}
