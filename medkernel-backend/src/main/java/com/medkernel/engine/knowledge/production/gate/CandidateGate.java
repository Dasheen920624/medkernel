package com.medkernel.engine.knowledge.production.gate;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 候选安全门禁项（AIK-STD-05，FR-1 逐条可测）。
 *
 * <p>每个实现＝一项确定性安全校验（不依赖模型）；返回 {@link GateItemResult}。新增门禁不破框架（类型无关、可扩展）。
 */
public interface CandidateGate {

    /** 门禁稳定码（落库与审计用）。 */
    String code();

    /** 对候选信封做确定性校验，返回逐项结果。 */
    GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context);
}
