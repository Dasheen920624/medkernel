package com.medkernel.engine.versioning;

import java.util.List;

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
    String traceId,
    AssetVersionSafetyPolicy safetyPolicy,
    AssetVersionOverridePolicy overridePolicy,
    List<AssetDependencyDeclaration> dependencies
) {
    public AssetVersionRegisterCommand {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public AssetVersionRegisterCommand(
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
            String traceId,
            AssetVersionSafetyPolicy safetyPolicy,
            AssetVersionOverridePolicy overridePolicy) {
        this(
            tenantId, assetType, assetIdentity, versionNo, organizationScope,
            applicableScope, content, contentHash, sourceRef, createdBy, traceId,
            safetyPolicy, overridePolicy, List.of()
        );
    }

    public AssetVersionRegisterCommand(
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
            String traceId) {
        this(
            tenantId, assetType, assetIdentity, versionNo, organizationScope,
            applicableScope, content, contentHash, sourceRef, createdBy, traceId,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, List.of()
        );
    }
}
