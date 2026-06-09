package com.medkernel.engine.integration.runtime;

import com.medkernel.engine.versioning.InheritanceOverrideMode;
import com.medkernel.engine.versioning.InheritancePropagation;
import com.medkernel.engine.versioning.VersionedAssetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 第三方组织覆盖登记请求。
 */
public record ThirdPartyOverrideRequest(
    @NotNull VersionedAssetType assetType,
    @NotBlank @Size(max = 256) String assetIdentity,
    @Size(max = 64) String inheritedVersionId,
    @Size(max = 64) String overrideVersionId,
    @NotBlank @Size(max = 64) String targetOrgUnitId,
    @NotBlank @Size(max = 512) String applicableScope,
    @NotNull InheritanceOverrideMode overrideMode,
    @NotBlank @Size(max = 2000) String diffSummary,
    @NotBlank @Size(max = 2000) String overrideReason,
    @NotBlank @Size(max = 2000) String impactScope,
    @NotNull InheritancePropagation propagation
) {
}
