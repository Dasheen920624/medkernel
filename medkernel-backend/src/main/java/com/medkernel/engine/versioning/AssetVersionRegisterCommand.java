package com.medkernel.engine.versioning;

/**
 * 登记配置资产草稿版本的命令。
 *
 * <p>{@code content} 与 {@code contentHash} 至少提供其一；同时提供时必须互相匹配。
 */
public record AssetVersionRegisterCommand(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionNo,
    String organizationScope,
    String applicableScope,
    String content,
    String contentHash,
    String sourceRef,
    String createdBy,
    String traceId
) {}
