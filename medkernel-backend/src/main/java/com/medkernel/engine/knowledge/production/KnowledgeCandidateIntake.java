package com.medkernel.engine.knowledge.production;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 候选入既有版本/审核链的接收端口（AIK-STD-13，FR-3）。
 *
 * <p>PR4 起真实物化：解析受控源 FK + 目标知识身份 → 经既有版本/审核/替换链落 {@code KnowledgeAssetVersion} +
 * {@code CandidateClassification}，据审核归口建 {@code ReviewAssignment}；返回真实物化版本引用。
 * 不造平行候选表；解析不出诚实拒收（铁律 #1）。
 */
public interface KnowledgeCandidateIntake {

    /**
     * 物化一条已校验且隔离通过的候选信封入既有版本/审核链。
     *
     * @param job 归属生产 job
     * @param candidate 候选信封（经 AIK-STD-01 校验 + §9 隔离守卫）
     * @param target 物化目标知识身份（生产方显式声明）
     * @param routing 审核归口决策
     * @return 真实物化版本引用标识
     */
    String intake(KnowledgeProductionJob job, KnowledgeAssetEnvelope candidate,
                  MaterializationTarget target, ReviewRoutingDecision routing);
}
