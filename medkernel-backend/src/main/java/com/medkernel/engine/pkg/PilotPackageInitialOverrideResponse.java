package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideMode;
import com.medkernel.engine.versioning.InheritanceOverrideStatus;
import com.medkernel.engine.versioning.InheritancePropagation;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 初始覆盖登记结果。
 */
public record PilotPackageInitialOverrideResponse(
    String overrideId,
    VersionedAssetType assetType,
    String assetIdentity,
    InheritanceOverrideMode overrideMode,
    InheritancePropagation propagation,
    InheritanceOverrideStatus lifecycleStatus,
    String orgPath
) {
    public static PilotPackageInitialOverrideResponse from(InheritanceOverride override) {
        return new PilotPackageInitialOverrideResponse(
            override.overrideId(),
            override.assetType(),
            override.assetIdentity(),
            override.overrideMode(),
            override.propagation(),
            override.lifecycleStatus(),
            override.orgPath()
        );
    }
}
