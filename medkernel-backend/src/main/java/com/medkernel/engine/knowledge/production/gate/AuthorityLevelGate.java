package com.medkernel.engine.knowledge.production.gate;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 门禁：可信分级（AIK-STD-05，FR-1 可信级）。
 *
 * <p>候选信封须带可信分级，且每条来源标 A–E 权威级（OPT-07 五级），缺失拒收（不以无级伪装可信）。
 */
@Component
public class AuthorityLevelGate implements CandidateGate {

    public static final String CODE = "AUTHORITY_LEVEL";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        if (candidate.trustLevel() == null) {
            return GateItemResult.fail(CODE, "候选可信分级缺失");
        }
        if (candidate.sources() != null) {
            for (AssetSourceRef source : candidate.sources()) {
                if (source != null && source.authorityLevel() == null) {
                    return GateItemResult.fail(CODE, "来源权威分级缺失");
                }
            }
        }
        return GateItemResult.pass(CODE);
    }
}
