package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.security.RoleCode;

/** 候选审核归口路由器单元测试。 */
class CandidateReviewRouterTest {

    private final CandidateReviewRouter router = new CandidateReviewRouter();

    @Test
    void allPipelinesRouteToEngineOperator() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.GENERAL, KnowledgeRiskLevel.LOW);
        assertThat(d.reviewerRole()).isEqualTo(RoleCode.ENGINE_OPERATOR);
    }

    @Test
    void riskDoesNotCreateAdditionalReviewSeats() {
        ReviewRoutingDecision low = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, KnowledgeRiskLevel.LOW);
        ReviewRoutingDecision high = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, KnowledgeRiskLevel.HIGH);
        assertThat(high.reviewerRole()).isEqualTo(low.reviewerRole());
    }

    @Test
    void decisionCarriesDomain() {
        assertThat(router.resolve(TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.PHARMACY,
            KnowledgeRiskLevel.HIGH).domain()).isEqualTo(KnowledgeDomain.PHARMACY);
    }
}
