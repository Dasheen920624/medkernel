package com.medkernel.engine.versioning;

import java.util.List;

/**
 * 发布前只读影响评估命令。
 */
public record ReleaseSimulationCommand(
    String tenantId,
    String candidateTenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String candidateVersionId,
    List<String> targetOrgUnitIds,
    String targetOrgPath,
    String applicableScope,
    RolloutPolicy rolloutPolicy,
    Integer replayDays,
    Integer replayLimit
) {
    public ReleaseSimulationCommand {
        targetOrgUnitIds = targetOrgUnitIds == null ? List.of() : List.copyOf(targetOrgUnitIds);
    }
}
