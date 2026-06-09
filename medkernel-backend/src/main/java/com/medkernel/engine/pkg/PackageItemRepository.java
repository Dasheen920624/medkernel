package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 包内资产条目 Repository 接口。
 */
@Repository
public interface PackageItemRepository extends ListCrudRepository<PackageItem, Long> {

    List<PackageItem> findByTenantIdAndPackageId(String tenantId, String packageId);

    List<PackageItem> findByTenantIdAndPackageIdIn(String tenantId, Set<String> packageIds);

    Optional<PackageItem> findByItemIdAndTenantId(String itemId, String tenantId);

    Optional<PackageItem> findByTenantIdAndPackageIdAndAssetTypeAndAssetId(
        String tenantId, String packageId, VersionedAssetType assetType, String assetId);

    List<PackageItem> findByTenantIdAndAssetTypeAndAssetId(
        String tenantId, VersionedAssetType assetType, String assetId);
}
