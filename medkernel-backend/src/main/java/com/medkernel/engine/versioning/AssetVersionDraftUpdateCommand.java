package com.medkernel.engine.versioning;

import java.util.List;

/**
 * 更新尚未发布的统一资产版本登记。
 *
 * <p>草稿期允许调整资产身份、生效域、来源、安全策略与内容指纹；版本号和版本业务 ID
 * 保持不变。发布后统一版本完全不可变。
 */
public record AssetVersionDraftUpdateCommand(
    String tenantId,
    String versionId,
    String assetIdentity,
    String organizationScope,
    String applicableScope,
    String content,
    String contentHash,
    String sourceRef,
    AssetVersionSafetyPolicy safetyPolicy,
    AssetVersionOverridePolicy overridePolicy,
    String actor,
    String traceId,
    List<AssetDependencyDeclaration> dependencies
) {
    public AssetVersionDraftUpdateCommand {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public AssetVersionDraftUpdateCommand(
            String tenantId,
            String versionId,
            String assetIdentity,
            String organizationScope,
            String applicableScope,
            String content,
            String contentHash,
            String sourceRef,
            AssetVersionSafetyPolicy safetyPolicy,
            AssetVersionOverridePolicy overridePolicy,
            String actor) {
        this(
            tenantId, versionId, assetIdentity, organizationScope, applicableScope, content, contentHash,
            sourceRef, safetyPolicy, overridePolicy, actor, null, List.of()
        );
    }
}
