package com.medkernel.engine.knowledge.production;

import org.springframework.stereotype.Service;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.security.RoleCode;

/**
 * 候选会签路由器（AIK-STD-13 PR3，FR-6/FR-7）。
 *
 * <p>纯确定性函数（B0，无上游、无模型）：按归属管道 + 领域 + 风险算出归口/领域会签角色 + 是否双签。
 * FR-7 院内覆盖角色边界：{@code TENANT_OVERLAY} 候选归口恒为机构知识治理员，永不平台归口。
 */
@Service
public class CandidateReviewRouter {

    public ReviewRoutingDecision resolve(TargetPipeline pipeline, KnowledgeDomain domain,
                                         KnowledgeRiskLevel risk) {
        RoleCode owner = switch (pipeline) {
            case PLATFORM_SOURCE -> RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR;
            case TENANT_OVERLAY -> RoleCode.KNOWLEDGE_GOVERNOR;
        };
        RoleCode domainRole = switch (domain) {
            case CLINICAL -> RoleCode.CLINICAL_GOVERNOR;
            case PHARMACY -> RoleCode.MEDICATION_SAFETY_USER;
            case TERMINOLOGY_REPORT -> RoleCode.DIAGNOSTIC_SERVICE_USER;
            case EVALUATION_INSURANCE -> RoleCode.QUALITY_GOVERNOR;
            case GENERAL -> owner;
        };
        boolean dualSign = risk == KnowledgeRiskLevel.HIGH;
        return new ReviewRoutingDecision(owner, domainRole, dualSign, domain);
    }
}
