package com.medkernel.engine.versioning;

/**
 * 解析指定组织可见配置资产版本的查询。
 */
public record InheritanceResolveQuery(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String applicableScope,
    String targetOrgUnitId
) {}
