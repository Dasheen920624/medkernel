package com.medkernel.engine.knowledge.production.generation;

import com.medkernel.engine.knowledge.production.ReviewRoutingDecision;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 已生成并提交的候选结果（AIK-STD-04）。
 *
 * <p>承载资产类型 + 归属生产 job 编码 + intake 返回的候选引用 + PR3 会签路由决策，供调用方回溯与审核分派。
 */
public record GeneratedCandidate(
    VersionedAssetType assetType,
    String jobCode,
    String candidateRef,
    ReviewRoutingDecision routing
) {
}
