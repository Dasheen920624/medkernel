package com.medkernel.engine.pkg;

import java.util.List;

import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Component;

/**
 * 统一知识包的资产类型感知发布门禁。
 */
@Component("packageAssetPermission")
public class PackageAssetPermissionEvaluator {

    private final KnowledgePackageRepository packageRepository;
    private final PackageItemRepository itemRepository;
    private final PermissionEvaluator permissionEvaluator;

    public PackageAssetPermissionEvaluator(
            KnowledgePackageRepository packageRepository,
            PackageItemRepository itemRepository,
            PermissionEvaluator permissionEvaluator) {
        this.packageRepository = packageRepository;
        this.itemRepository = itemRepository;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * 通用包发布权限可发布任意包；领域发布权限仅可发布当前租户的同领域纯资产包。
     */
    public boolean canPublish(String packageId) {
        if (permissionEvaluator.has(PermissionCode.PACKAGE_PUBLISH)) {
            return true;
        }
        OrgScope scope = RequestContext.currentOrgScope();
        if (packageId == null || packageId.isBlank() || scope == null || !scope.hasTenant()) {
            return false;
        }
        String tenantId = scope.tenantId();
        if (packageRepository.findByPackageIdAndTenantId(packageId, tenantId).isEmpty()) {
            return false;
        }
        List<VersionedAssetType> assetTypes = itemRepository
            .findByTenantIdAndPackageId(tenantId, packageId)
            .stream()
            .map(PackageItem::assetType)
            .toList();
        return PackageAssetPermissionPolicy.publishPermission(assetTypes)
            .map(permissionEvaluator::has)
            .orElse(false);
    }
}
