package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 机构视角下最终生效的知识包条目。
 */
public record EffectivePackageItem(
    VersionedAssetType assetType,
    String assetId,
    String declaredVersion,
    String effectiveVersion,
    String sourceTenantId,
    String sourceOrgPath,
    SourceTier sourceTier,
    boolean inherited,
    boolean overridden,
    boolean resolvedByUnifiedVersioning,
    String sourceVersionId,
    String contentHash
) {}
