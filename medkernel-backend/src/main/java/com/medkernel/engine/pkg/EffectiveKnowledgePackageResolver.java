package com.medkernel.engine.pkg;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.BatchResolvedAsset;
import com.medkernel.engine.versioning.InheritanceBatchResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.VersionedAssetIdentity;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 将平台知识包基线解析为指定机构实际生效的包快照。
 */
@Service
public class EffectiveKnowledgePackageResolver {

    private static final String GLOBAL_SCOPE = "ALL";

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository itemRepository;
    private final InheritanceResolver inheritanceResolver;
    private final PackageEntitlementService entitlementService;

    public EffectiveKnowledgePackageResolver(
            KnowledgePackageRepository packageRepository,
            PackageItemRepository itemRepository,
            InheritanceResolver inheritanceResolver,
            PackageEntitlementService entitlementService) {
        this.packageRepository = packageRepository;
        this.itemRepository = itemRepository;
        this.inheritanceResolver = inheritanceResolver;
        this.entitlementService = entitlementService;
    }

    public EffectiveKnowledgePackageResponse resolve(
            String tenantId,
            String packageCode,
            String packageVersion,
            String targetOrgUnitId) {
        return resolve(tenantId, packageCode, packageVersion, targetOrgUnitId, null, null);
    }

    public EffectiveKnowledgePackageResponse resolve(
            String tenantId,
            String packageCode,
            String packageVersion,
            String targetOrgUnitId,
            String applicableScope,
            Instant effectiveAt) {
        String effectiveTenantId = required(tenantId, "租户 ID");
        String effectivePackageCode = required(packageCode, "知识包编码");
        String effectivePackageVersion = required(packageVersion, "知识包版本");
        String effectiveTargetOrgUnitId = required(targetOrgUnitId, "目标组织 ID");

        KnowledgePackage pack = packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                PlatformTenant.ID, effectivePackageCode, effectivePackageVersion)
            .orElseThrow(() -> new ApiException(
                ErrorCode.NOT_FOUND,
                "平台基线知识包不存在: " + effectivePackageCode + "@" + effectivePackageVersion));
        if (pack.status() != KnowledgePackageStatus.PUBLISHED && pack.status() != KnowledgePackageStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "平台基线知识包尚未发布，不能解析有效包: " + pack.packageId());
        }
        entitlementService.assertUsable(effectiveTenantId, pack);

        List<PackageItem> declaredItems =
            itemRepository.findByTenantIdAndPackageId(PlatformTenant.ID, pack.packageId());
        Map<VersionedAssetIdentity, PackageItem> declaredByIdentity = new LinkedHashMap<>();
        for (PackageItem item : declaredItems) {
            declaredByIdentity.put(new VersionedAssetIdentity(item.assetType(), item.assetId()), item);
        }
        List<BatchResolvedAsset> batch = inheritanceResolver.resolveBatch(new InheritanceBatchResolveQuery(
            effectiveTenantId,
            List.copyOf(declaredByIdentity.keySet()),
            scopes(applicableScope, effectivePackageVersion),
            effectiveTargetOrgUnitId,
            effectiveAt));
        Map<VersionedAssetIdentity, BatchResolvedAsset> resolvedByIdentity = new LinkedHashMap<>();
        for (BatchResolvedAsset item : batch) {
            resolvedByIdentity.put(item.identity(), item);
        }
        List<EffectivePackageItem> effectiveItems = new ArrayList<>();
        List<EffectivePackageExclusion> excludedItems = new ArrayList<>();

        for (PackageItem declaredItem : declaredItems) {
            VersionedAssetIdentity identity =
                new VersionedAssetIdentity(declaredItem.assetType(), declaredItem.assetId());
            BatchResolvedAsset batchItem = resolvedByIdentity.get(identity);
            if (batchItem == null) {
                throw new ApiException(
                    ErrorCode.NOT_FOUND,
                    "有效包条目未接入统一版本资产: " + itemIdentity(declaredItem));
            }
            ResolvedAssetVersion resolved = batchItem.resolution();
            if (resolved.disabled()) {
                excludedItems.add(new EffectivePackageExclusion(
                    declaredItem.assetType(),
                    declaredItem.assetId(),
                    declaredItem.assetVersion(),
                    "机构有效版本已被覆盖停用",
                    resolved.sourceOrgPath()));
                continue;
            }
            AssetVersion version = resolved.version();
            if (version == null) {
                throw new ApiException(ErrorCode.CONFLICT, "统一继承解析返回空版本: " + itemIdentity(declaredItem));
            }
            effectiveItems.add(toEffectiveItem(identity, declaredItem.assetVersion(), resolved));
        }

        for (BatchResolvedAsset batchItem : batch) {
            if (!batchItem.added() || declaredByIdentity.containsKey(batchItem.identity())) {
                continue;
            }
            ResolvedAssetVersion resolved = batchItem.resolution();
            if (resolved.disabled()) {
                excludedItems.add(new EffectivePackageExclusion(
                    batchItem.identity().assetType(),
                    batchItem.identity().assetIdentity(),
                    null,
                    "机构独有资产已被下级覆盖停用",
                    resolved.sourceOrgPath()));
                continue;
            }
            AssetVersion version = resolved.version();
            if (version == null) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "ADD 独有资产解析返回空版本: "
                        + batchItem.identity().assetType() + ":" + batchItem.identity().assetIdentity());
            }
            effectiveItems.add(toEffectiveItem(batchItem.identity(), version.versionNo(), resolved));
        }

        return new EffectiveKnowledgePackageResponse(
            effectiveTenantId,
            effectiveTargetOrgUnitId,
            pack.packageId(),
            pack.packageCode(),
            pack.packageVersion(),
            effectiveItems,
            excludedItems,
            List.of());
    }

    private EffectivePackageItem toEffectiveItem(
            VersionedAssetIdentity identity,
            String declaredVersion,
            ResolvedAssetVersion resolved) {
        AssetVersion version = resolved.version();
        return new EffectivePackageItem(
            identity.assetType(),
            identity.assetIdentity(),
            declaredVersion,
            version.versionNo(),
            version.tenantId(),
            resolved.sourceOrgPath(),
            resolved.sourceTier(),
            resolved.inherited(),
            resolved.overridden(),
            true,
            version.versionId(),
            version.contentHash());
    }

    private List<String> scopes(String applicableScope, String packageVersion) {
        Set<String> values = new LinkedHashSet<>();
        if (applicableScope != null && !applicableScope.isBlank()) {
            values.add(applicableScope.trim());
        }
        values.add(required(packageVersion, "知识包版本"));
        values.add(GLOBAL_SCOPE);
        return List.copyOf(values);
    }

    private String itemIdentity(PackageItem item) {
        return item.assetType() + ":" + item.assetId() + "@" + item.assetVersion();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }
}
