package com.medkernel.engine.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.VersionedAssetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 独立声明式配置资产新增或更新请求。
 */
public record DeclarativeAssetUpsertRequest(
    @NotNull VersionedAssetType assetType,
    @NotBlank @Size(max = 160) String assetIdentity,
    @Size(max = 500) String applicableScope,
    @NotBlank @Size(max = 1000) String sourceRef,
    @NotNull JsonNode content
) {
}
