package com.medkernel.engine.versioning;

import java.util.Optional;

/**
 * 按机构生效版本解析声明式资产不可变正文的端口。
 */
@FunctionalInterface
public interface DeclarativeAssetRuntimePort {

    Optional<ResolvedDeclarativeAsset> resolve(
        String tenantId,
        String runtimeReleaseId,
        VersionedAssetType assetType,
        String assetIdentity
    );

    static DeclarativeAssetRuntimePort unavailable() {
        return (tenantId, runtimeReleaseId, assetType, assetIdentity) -> Optional.empty();
    }
}
