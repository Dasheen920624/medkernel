package com.medkernel.engine.versioning;

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
    String actor
) {}
