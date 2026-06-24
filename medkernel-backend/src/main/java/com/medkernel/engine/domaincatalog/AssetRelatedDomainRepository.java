package com.medkernel.engine.domaincatalog;

import java.util.List;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 稳定资产相关领域仓储。
 */
@Repository
public interface AssetRelatedDomainRepository
        extends ListCrudRepository<AssetRelatedDomain, Long> {

    List<AssetRelatedDomain> findByTenantIdAndAssetTypeAndAssetIdentityOrderByDomainCodeAsc(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity
    );
}
