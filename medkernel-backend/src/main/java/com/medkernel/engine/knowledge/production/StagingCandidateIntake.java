package com.medkernel.engine.knowledge.production;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * PR1 默认候选接收实现（AIK-STD-13，FR-3 暂存桩）。
 *
 * <p>诚实分寸：既有候选物化链深耦合解析管道（AIK-STD-04/10·P2-C，需身份/源 FK/8 态去重），
 * 故 PR1 不过早耦合——本实现返回暂存引用标识（{@code staged:<资产身份>}），<b>不伪装已物化入权威/版本链</b>。
 * 真实物化随解析管道接线时以正式实现替换本桩（端口契约不变）。
 */
@Component
public class StagingCandidateIntake implements KnowledgeCandidateIntake {

    @Override
    public String intake(KnowledgeProductionJob job, KnowledgeAssetEnvelope candidate) {
        return "staged:" + candidate.assetIdentity();
    }
}
