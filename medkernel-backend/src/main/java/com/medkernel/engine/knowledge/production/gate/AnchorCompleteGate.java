package com.medkernel.engine.knowledge.production.gate;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 门禁：锚点完整（AIK-STD-05，FR-1 锚点完整）。
 *
 * <p>每条来源引用须为 {@code 来源编码:版本:锚点} 三段且各段非空（与 {@code SourceReferenceResolver} 对齐，
 * 确保候选物化时可回查受控源 FK）。
 */
@Component
public class AnchorCompleteGate implements CandidateGate {

    public static final String CODE = "ANCHOR_COMPLETE";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        if (candidate.sources() == null || candidate.sources().isEmpty()) {
            return GateItemResult.fail(CODE, "无来源锚点可校验");
        }
        for (AssetSourceRef source : candidate.sources()) {
            String ref = source == null ? null : source.sourceRef();
            if (ref == null) {
                return GateItemResult.fail(CODE, "来源锚点为空");
            }
            String[] parts = ref.split(":", 3);
            if (parts.length < 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                return GateItemResult.fail(CODE, "锚点引用不完整（须 来源编码:版本:锚点 三段）：" + ref);
            }
        }
        return GateItemResult.pass(CODE);
    }
}
