package com.medkernel.engine.versioning;

import java.util.List;

import com.medkernel.engine.context.ContextSnapshot;

/**
 * 资产类型历史回放执行器。
 */
public interface ReleaseSimulationReplayEvaluator {

    boolean supports(VersionedAssetType assetType);

    ReleaseSimulationResult.Replay replay(
        ReleaseSimulationCommand command,
        AssetVersion currentVersion,
        AssetVersion candidateVersion,
        List<ContextSnapshot> snapshots
    );
}
