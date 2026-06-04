package com.medkernel.engine.pkg;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 包内资产条目 Repository 接口。
 */
@Repository
public interface PackageItemRepository extends ListCrudRepository<PackageItem, Long> {

    List<PackageItem> findByTenantIdAndPackageId(String tenantId, String packageId);

    Optional<PackageItem> findByItemIdAndTenantId(String itemId, String tenantId);

    Optional<PackageItem> findByTenantIdAndPackageIdAndAssetTypeAndAssetId(
        String tenantId, String packageId, PackageItemAssetType assetType, String assetId);

    List<PackageItem> findByTenantIdAndAssetTypeAndAssetId(
        String tenantId, PackageItemAssetType assetType, String assetId);
}
