package com.medkernel.engine.knowledge.production;

import org.springframework.stereotype.Service;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.security.RoleCode;

/**
 * 候选审核归口路由器。
 *
 * <p>知识生产统一由医疗引擎运营职责审核；领域与风险仍保留为技术门禁和展示依据，
 * 不再派生专家角色或高风险双签席位。
 */
@Service
public class CandidateReviewRouter {

    public ReviewRoutingDecision resolve(TargetPipeline pipeline, KnowledgeDomain domain,
                                         KnowledgeRiskLevel risk) {
        return new ReviewRoutingDecision(
            RoleCode.ENGINE_OPERATOR,
            domain
        );
    }
}
