package com.medkernel.engine.versioning;

import java.time.Instant;

/**
 * 解析指定组织可见配置资产版本的查询。
 */
public record InheritanceResolveQuery(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String applicableScope,
    String targetOrgUnitId,
    Instant effectiveAt
) {
    public InheritanceResolveQuery(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            String targetOrgUnitId) {
        this(tenantId, assetType, assetIdentity, applicableScope, targetOrgUnitId, null);
    }
}
