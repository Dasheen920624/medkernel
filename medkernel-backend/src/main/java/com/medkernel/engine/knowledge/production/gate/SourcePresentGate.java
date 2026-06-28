package com.medkernel.engine.knowledge.production.gate;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 门禁：来源真实性（结构层）（AIK-STD-05，FR-1 来源真实性）。
 *
 * <p>候选须绑 ≥1 来源且每条来源标识非空（核心 §7 来源可溯 / 铁律 #1 无源拒收）。深层来源可解析校验由来源解析门负责。
 */
@Component
public class SourcePresentGate implements CandidateGate {

    public static final String CODE = "SOURCE_PRESENT";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        if (candidate.sources() == null || candidate.sources().isEmpty()) {
            return GateItemResult.fail(CODE, "候选无来源（无源资产拒收）");
        }
        for (AssetSourceRef source : candidate.sources()) {
            if (source == null || source.sourceRef() == null || source.sourceRef().isBlank()) {
                return GateItemResult.fail(CODE, "来源引用为空");
            }
        }
        return GateItemResult.pass(CODE);
    }
}
