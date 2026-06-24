package com.medkernel.engine.knowledge.production.generation;

import com.medkernel.engine.knowledge.production.ReviewRoutingDecision;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 已生成并提交的候选结果（AIK-STD-04）。
 *
 * <p>承载资产类型、生产 job、候选引用与审核归口，供调用方回溯。
 */
public record GeneratedCandidate(
    VersionedAssetType assetType,
    String jobCode,
    String candidateRef,
    ReviewRoutingDecision routing
) {
}
