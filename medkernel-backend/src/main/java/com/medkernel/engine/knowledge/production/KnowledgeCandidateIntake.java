package com.medkernel.engine.knowledge.production;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 候选入既有版本/审核链的接收端口（AIK-STD-13，FR-3）。
 *
 * <p>编排层经本端口把校验+隔离通过的候选信封交既有版本/审核/替换链物化，<b>不造平行候选表</b>。
 * 既有 {@code KnowledgeVersionService.classifyCandidate} 物化深耦合（需既有 knowledge_identity + 源 FK + 8 态去重，
 * 属 AIK-STD-04/10 解析管道·P2-C）；PR1 提供默认实现仅记血缘，真实物化随解析管道接线。
 */
public interface KnowledgeCandidateIntake {

    /**
     * 接收一条已校验且隔离通过的候选信封，返回候选引用标识（供血缘回溯）。
     *
     * @param job 归属生产 job
     * @param candidate 候选信封（经 AIK-STD-01 校验 + §9 隔离守卫）
     * @return 候选引用标识
     */
    String intake(KnowledgeProductionJob job, KnowledgeAssetEnvelope candidate);
}
