package com.medkernel.engine.versioning;

/**
 * 登记组织局部覆盖解释的命令。
 */
public record InheritanceOverrideRegisterCommand(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String inheritedVersionId,
    String overrideVersionId,
    String targetOrgUnitId,
    String applicableScope,
    InheritanceOverrideMode overrideMode,
    String diffSummary,
    String overrideReason,
    String impactScope,
    String createdBy,
    String traceId,
    InheritancePropagation propagation
) {
    /** 兼容未显式声明传播范围的旧调用：默认 INHERITABLE（下级复用）。 */
    public InheritanceOverrideRegisterCommand(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String inheritedVersionId,
            String overrideVersionId,
            String targetOrgUnitId,
            String applicableScope,
            InheritanceOverrideMode overrideMode,
            String diffSummary,
            String overrideReason,
            String impactScope,
            String createdBy,
            String traceId) {
        this(
            tenantId, assetType, assetIdentity, inheritedVersionId, overrideVersionId,
            targetOrgUnitId, applicableScope, overrideMode, diffSummary, overrideReason,
            impactScope, createdBy, traceId, InheritancePropagation.INHERITABLE
        );
    }
}
