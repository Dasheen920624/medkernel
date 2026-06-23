package com.medkernel.engine.domaincatalog;

import java.util.Optional;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 稳定资产主领域归类仓储。
 */
@Repository
public interface AssetDomainProfileRepository
        extends ListCrudRepository<AssetDomainProfile, Long> {

    Optional<AssetDomainProfile> findByTenantIdAndAssetTypeAndAssetIdentity(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity
    );
}
