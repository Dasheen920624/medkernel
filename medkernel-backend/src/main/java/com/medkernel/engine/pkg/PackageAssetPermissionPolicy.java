package com.medkernel.engine.pkg;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;

import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 统一知识包的领域发布权限映射。
 *
 * <p>仅单一领域资产包可复用领域发布权限；空包、混合包和基础设施包必须使用
 * {@code package.publish}，避免术语发布权限越权到其他配置资产。
 */
final class PackageAssetPermissionPolicy {

    private PackageAssetPermissionPolicy() {
    }

    static Optional<PermissionCode> publishPermission(Collection<VersionedAssetType> assetTypes) {
        if (assetTypes == null || assetTypes.isEmpty()) {
            return Optional.empty();
        }
        EnumSet<VersionedAssetType> distinctTypes = EnumSet.copyOf(assetTypes);
        if (distinctTypes.size() != 1) {
            return Optional.empty();
        }
        return switch (distinctTypes.iterator().next()) {
            case TERMINOLOGY -> Optional.of(PermissionCode.TERM_PUBLISH);
            case PATHWAY -> Optional.of(PermissionCode.PATHWAY_PUBLISH);
            case RULE -> Optional.of(PermissionCode.RULE_PUBLISH);
            case KNOWLEDGE -> Optional.of(PermissionCode.KNOWLEDGE_PUBLISH);
            case EVALUATION -> Optional.of(PermissionCode.EVALUATION_PUBLISH);
            default -> Optional.empty();
        };
    }
}
