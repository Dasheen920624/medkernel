package com.medkernel.engine.authoring;

import java.util.Optional;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 统一创作资产收藏仓储。
 */
@Repository
public interface AuthoringAssetFavoriteRepository extends ListCrudRepository<AuthoringAssetFavorite, Long> {

    boolean existsByTenantIdAndUserIdAndAssetTypeAndAssetId(
        String tenantId, String userId, VersionedAssetType assetType, String assetId);

    Optional<AuthoringAssetFavorite> findByTenantIdAndUserIdAndAssetTypeAndAssetId(
        String tenantId, String userId, VersionedAssetType assetType, String assetId);
}
