package com.medkernel.engine.pkg;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.engine.versioning.InheritanceOverrideMode;
import com.medkernel.engine.versioning.InheritancePropagation;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 首发推荐引用时可选登记的初始覆盖。
 */
public record PilotPackageInitialOverrideRequest(
    @JsonProperty("asset_type") VersionedAssetType assetType,
    @JsonProperty("asset_identity") String assetIdentity,
    @JsonProperty("inherited_version_id") String inheritedVersionId,
    @JsonProperty("override_version_id") String overrideVersionId,
    @JsonProperty("target_org_unit_id") String targetOrgUnitId,
    @JsonProperty("applicable_scope") String applicableScope,
    @JsonProperty("override_mode") InheritanceOverrideMode overrideMode,
    @JsonProperty("propagation") InheritancePropagation propagation,
    @JsonProperty("diff_summary") String diffSummary,
    @JsonProperty("override_reason") String overrideReason,
    @JsonProperty("impact_scope") String impactScope
) {
}
