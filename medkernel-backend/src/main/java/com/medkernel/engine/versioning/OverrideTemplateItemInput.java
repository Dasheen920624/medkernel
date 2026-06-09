package com.medkernel.engine.versioning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建覆盖模板的资产项。
 */
public record OverrideTemplateItemInput(
    @NotNull VersionedAssetType assetType,
    @NotBlank @Size(max = 256) String assetIdentity,
    @Size(max = 64) String inheritedVersionId,
    @Size(max = 64) String sourceOverrideVersionId,
    @NotNull InheritanceOverrideMode overrideMode,
    @NotNull InheritancePropagation propagation,
    @NotBlank @Size(max = 512) String applicableScope,
    @NotBlank @Size(max = 2000) String diffSummary,
    @NotBlank @Size(max = 1000) String overrideReason
) {
}
