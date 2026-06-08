package com.medkernel.engine.authoring;

import java.util.List;
import java.util.Optional;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 统一创作资产编目仓储。
 */
@Repository
public interface AuthoringAssetProfileRepository extends ListCrudRepository<AuthoringAssetProfile, Long> {

    Optional<AuthoringAssetProfile> findByTenantIdAndAssetTypeAndAssetId(
        String tenantId, VersionedAssetType assetType, String assetId);

    List<AuthoringAssetProfile> findByTenantIdAndAssetTypeAndAssetIdIn(
        String tenantId, VersionedAssetType assetType, List<String> assetIds);
}
