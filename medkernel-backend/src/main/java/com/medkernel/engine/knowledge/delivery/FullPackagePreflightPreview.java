package com.medkernel.engine.knowledge.delivery;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.release.PlatformUpgradeDiffItem;
import com.medkernel.engine.release.PlatformUpgradeDiffSummary;
import com.medkernel.engine.release.PlatformUpgradeRuntimeSnapshot;

/**
 * 绑定真实包和当前医院状态的不可变差异、冲突、依赖与撤回预览。
 */
public record FullPackagePreflightPreview(
    String schemaVersion,
    String preflightId,
    FullPackagePreflightStatus status,
    String tenantId,
    String hospitalId,
    boolean runtimeMutation,
    String authorityId,
    String deliveryId,
    long releaseSequence,
    String manifestDigest,
    String platformReleaseIdentity,
    String packageFileDigest,
    long packageFileSize,
    String quarantineCoordinate,
    PlatformUpgradeRuntimeSnapshot currentRuntime,
    PlatformUpgradeDiffSummary diffSummary,
    List<PlatformUpgradeDiffItem> differences,
    ImpactSummary impactSummary,
    List<FullPackageReleaseDocument.Withdrawal> withdrawals,
    int archiveEntryCount,
    long expandedBytes,
    Instant createdAt,
    String previewDigest
) {
    public FullPackagePreflightPreview {
        differences = differences == null ? List.of() : List.copyOf(differences);
        withdrawals = withdrawals == null ? List.of() : List.copyOf(withdrawals);
    }

    /** 依赖传播和撤回对当前医院的只读影响计数。 */
    public record ImpactSummary(
        int dependencyEdges,
        int changedDependencyEdges,
        int withdrawalCount,
        int activeWithdrawalImpactCount
    ) {
    }
}
