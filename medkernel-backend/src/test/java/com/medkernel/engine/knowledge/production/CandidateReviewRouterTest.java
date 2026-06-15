package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.security.RoleCode;

/**
 * 候选会签路由器单元测试（AIK-STD-13 PR3，FR-6/FR-7，纯确定性 B0）。
 */
class CandidateReviewRouterTest {

    private final CandidateReviewRouter router = new CandidateReviewRouter();

    @Test
    void platformSourceRoutesToPlatformKnowledgeGovernorAsOwner() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.GENERAL, KnowledgeRiskLevel.LOW);
        assertThat(d.ownerReviewerRole()).isEqualTo(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR);
    }

    @Test
    void tenantOverlayRoutesToOrgKnowledgeGovernorNeverPlatform() {
        // FR-7 院内覆盖角色边界：院内候选归口恒为机构知识治理员，永不平台归口。
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, KnowledgeRiskLevel.LOW);
        assertThat(d.ownerReviewerRole()).isEqualTo(RoleCode.KNOWLEDGE_GOVERNOR);
        assertThat(d.ownerReviewerRole()).isNotEqualTo(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR);
    }

    @Test
    void clinicalDomainCosignsClinicalGovernor() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.CLINICAL_GOVERNOR);
    }

    @Test
    void pharmacyDomainCosignsMedicationSafetyUser() {
        // 药学＝领域（非资产类型）：路由药事安全人员。
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.PHARMACY, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.MEDICATION_SAFETY_USER);
    }

    @Test
    void terminologyReportDomainCosignsDiagnosticServiceUser() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.TERMINOLOGY_REPORT, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.DIAGNOSTIC_SERVICE_USER);
    }

    @Test
    void evaluationInsuranceDomainCosignsQualityGovernor() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.EVALUATION_INSURANCE, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.QUALITY_GOVERNOR);
    }

    @Test
    void generalDomainCosignerEqualsOwnerRole() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.GENERAL, KnowledgeRiskLevel.LOW);
        assertThat(d.domainReviewerRole()).isEqualTo(d.ownerReviewerRole());
        assertThat(d.domainReviewerRole()).isEqualTo(RoleCode.KNOWLEDGE_GOVERNOR);
    }

    @Test
    void highRiskRequiresDualSign() {
        ReviewRoutingDecision d = router.resolve(
            TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL, KnowledgeRiskLevel.HIGH);
        assertThat(d.requiresDualSign()).isTrue();
    }

    @Test
    void nonHighRiskIsSingleSign() {
        assertThat(router.resolve(TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL,
            KnowledgeRiskLevel.LOW).requiresDualSign()).isFalse();
        assertThat(router.resolve(TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL,
            KnowledgeRiskLevel.MEDIUM).requiresDualSign()).isFalse();
    }

    @Test
    void decisionCarriesDomain() {
        assertThat(router.resolve(TargetPipeline.PLATFORM_SOURCE, KnowledgeDomain.PHARMACY,
            KnowledgeRiskLevel.HIGH).domain()).isEqualTo(KnowledgeDomain.PHARMACY);
    }
}
