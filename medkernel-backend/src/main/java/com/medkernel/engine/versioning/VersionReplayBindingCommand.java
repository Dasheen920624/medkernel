package com.medkernel.engine.versioning;

/**
 * 历史重放绑定命令。
 */
public record VersionReplayBindingCommand(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionId,
    String patientSnapshotId,
    String runtimeEventId,
    String resultHash,
    String actor,
    String traceId
) {}
