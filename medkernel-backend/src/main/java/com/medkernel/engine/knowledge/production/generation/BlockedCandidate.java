package com.medkernel.engine.knowledge.production.generation;

import java.util.List;

import com.medkernel.engine.knowledge.production.gate.GateItemResult;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 被安全门禁拦截、未提审的候选（AIK-STD-05 接入 AIK-STD-04）。
 *
 * <p>承载资产类型 + 归属生产 job + 未通过的门禁项（诚实报因）；不静默放行，不伪造提审（铁律 #1）。
 */
public record BlockedCandidate(
    VersionedAssetType assetType,
    String jobCode,
    List<GateItemResult> failedGates
) {
    public BlockedCandidate {
        failedGates = failedGates == null ? List.of() : List.copyOf(failedGates);
    }
}
