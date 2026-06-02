package com.medkernel.engine.versioning;

/**
 * 登记组织局部覆盖解释的命令。
 */
public record InheritanceOverrideRegisterCommand(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String inheritedVersionId,
    String overrideVersionId,
    String targetOrgUnitId,
    String applicableScope,
    InheritanceOverrideMode overrideMode,
    String diffSummary,
    String overrideReason,
    String impactScope,
    String createdBy,
    String traceId
) {}
