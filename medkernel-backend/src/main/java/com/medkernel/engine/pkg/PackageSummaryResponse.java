package com.medkernel.engine.pkg;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 知识包列表摘要，附带包内资产分类与主条目标识。
 */
public record PackageSummaryResponse(
    Long id,
    String packageId,
    String tenantId,
    String packageCode,
    String packageVersion,
    String name,
    String description,
    PackageAccessPolicy accessPolicy,
    KnowledgePackageStatus status,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    String traceId,
    List<VersionedAssetType> assetTypes,
    String primaryAssetId,
    String primaryAssetVersion,
    int itemCount,
    String organizationScope,
    String applicableScope,
    String sourceRef
) {
    public static PackageSummaryResponse from(
            KnowledgePackage pack,
            List<PackageItem> items,
            AssetVersion packageVersion) {
        List<VersionedAssetType> assetTypes = items.stream()
            .map(PackageItem::assetType)
            .distinct()
            .toList();
        PackageItem primary = items.isEmpty() ? null : items.getFirst();
        return new PackageSummaryResponse(
            pack.id(),
            pack.packageId(),
            pack.tenantId(),
            pack.packageCode(),
            pack.packageVersion(),
            pack.name(),
            pack.description(),
            pack.accessPolicy(),
            pack.status(),
            pack.createdAt(),
            pack.createdBy(),
            pack.updatedAt(),
            pack.updatedBy(),
            pack.traceId(),
            assetTypes,
            primary == null ? null : primary.assetId(),
            primary == null ? null : primary.assetVersion(),
            items.size(),
            packageVersion == null ? null : packageVersion.organizationScope(),
            packageVersion == null ? null : packageVersion.applicableScope(),
            packageVersion == null ? null : packageVersion.sourceRef()
        );
    }
}
