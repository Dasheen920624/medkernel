package com.medkernel.engine.knowledge.production.gate;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;

/**
 * 门禁：适用域（AIK-STD-05，FR-1 适用域）。
 *
 * <p>候选须声明组织作用域，确保归属明确、可按 org 隔离与路由（缺失则无法判定适用边界，拒收）。
 */
@Component
public class ApplicableScopeGate implements CandidateGate {

    public static final String CODE = "APPLICABLE_SCOPE";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        if (candidate.orgScope() == null || candidate.orgScope().isBlank()) {
            return GateItemResult.fail(CODE, "适用域（组织作用域）缺失");
        }
        return GateItemResult.pass(CODE);
    }
}
