package com.medkernel.engine.knowledge.production.generation;

import com.medkernel.engine.knowledge.production.ReviewRoutingDecision;
import com.medkernel.engine.versioning.VersionedAssetType;

/** 已生成并提交的候选结果（AIK-STD-04）：资产类型 + 归属生产 job + 候选引用 + 会签路由。 */
public record GeneratedCandidate(
    VersionedAssetType assetType,
    String jobCode,
    String candidateRef,
    ReviewRoutingDecision routing
) {
}
