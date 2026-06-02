package com.medkernel.engine.versioning;

/**
 * 版本回滚命令。
 */
public record VersionRollbackCommand(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String currentVersionId,
    String targetVersionId,
    String confirmedCurrentVersion,
    String confirmedTargetVersion,
    String reason,
    Boolean confirmedHighRisk,
    String actor,
    String traceId
) {}
