package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class RuleGovernanceServiceTest {

    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-06-07T13:30:00Z"), ZoneOffset.UTC);

    private RuleGovernanceRepository governanceRepository;
    private RuleSignoffRepository signoffRepository;
    private RuleGovernanceService service;

    @BeforeEach
    void setUp() {
        governanceRepository = mock(RuleGovernanceRepository.class);
        signoffRepository = mock(RuleSignoffRepository.class);
        when(governanceRepository.save(any(RuleGovernance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(signoffRepository.save(any(RuleSignoff.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        service = new RuleGovernanceService(governanceRepository, signoffRepository, CLOCK);
    }

    @Test
    void initializesHighRiskRuleWithTwoRequiredCommitteeSignoffs() {
        RuleGovernance governance = service.initialize(
            "tenant-A", "version-1", RuleRiskLevel.HIGH, "author-1", "trace-1");

        assertThat(governance.state()).isEqualTo(RuleGovernanceState.DRAFT);
        assertThat(governance.requiredSignoffs()).isEqualTo(2);
        assertThat(governance.authorId()).isEqualTo("author-1");
        verify(governanceRepository).save(governance);
    }

    @Test
    void rejectsAuthorSelfReviewAndKeepsPeerReviewState() {
        RuleGovernance peerReview = governance(RuleGovernanceState.PEER_REVIEW, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(peerReview));

        assertThatThrownBy(() -> service.recordSignoff(
                "tenant-A",
                "version-1",
                RuleSignoffStage.PEER_REVIEW,
                RuleSignoffDecision.APPROVED,
                "同行评审通过",
                "author-1",
                RoleCode.KNOWLEDGE_GOVERNOR,
                "trace-2"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("作者不能审核自己的规则")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(signoffRepository, never()).save(any());
        verify(governanceRepository, never()).save(any());
    }

    @Test
    void highRiskRuleRequiresDistinctCommitteeSignersBeforeShadow() {
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE, 2);
        RuleSignoff first = signoff("signer-1", RuleSignoffStage.COMMITTEE);
        RuleSignoff second = signoff("signer-2", RuleSignoffStage.COMMITTEE);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(committee));
        when(signoffRepository.findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
            "version-1", "tenant-A"))
            .thenReturn(List.of(first), List.of(first, second));

        assertThatThrownBy(() -> service.transition(
                "tenant-A",
                "version-1",
                RuleGovernanceState.SHADOW,
                "进入影子验证",
                "publisher-1",
                "trace-3"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("仍需 1 名独立委员会成员会签")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        RuleGovernance shadow = service.transition(
            "tenant-A",
            "version-1",
            RuleGovernanceState.SHADOW,
            "会签完成，进入影子验证",
            "publisher-1",
            "trace-4");

        assertThat(shadow.state()).isEqualTo(RuleGovernanceState.SHADOW);
        assertThat(shadow.lastReason()).isEqualTo("会签完成，进入影子验证");
    }

    @Test
    void rejectsDuplicateCommitteeSignoffFromSamePerson() {
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(committee));
        when(signoffRepository.existsByRuleVersionIdAndTenantIdAndStageAndReviewRoundAndSignerId(
            "version-1", "tenant-A", RuleSignoffStage.COMMITTEE, 1, "signer-1"))
            .thenReturn(true);

        assertThatThrownBy(() -> service.recordSignoff(
                "tenant-A",
                "version-1",
                RuleSignoffStage.COMMITTEE,
                RuleSignoffDecision.APPROVED,
                "重复签署",
                "signer-1",
                RoleCode.CLINICAL_GOVERNOR,
                "trace-5"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不能重复会签")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(signoffRepository, never()).save(any());
    }

    @Test
    void insuranceManagerCanSignCommitteeForInsuranceRules() {
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(committee));

        RuleGovernance updated = service.recordSignoff(
            "tenant-A",
            "version-1",
            RuleSignoffStage.COMMITTEE,
            RuleSignoffDecision.APPROVED,
            "医保规则会签通过",
            "insurance-1",
            RoleCode.QUALITY_GOVERNOR,
            "trace-insurance");

        assertThat(updated.state()).isEqualTo(RuleGovernanceState.COMMITTEE);
        verify(signoffRepository).save(org.mockito.ArgumentMatchers.argThat(signoff ->
            signoff.signerRole().equals(RoleCode.QUALITY_GOVERNOR.code())
                && signoff.signerId().equals("insurance-1")
        ));
    }

    @Test
    void clinicalPharmacistCanSignPeerReviewForDrugInteractionRules() {
        RuleGovernance peerReview = governance(RuleGovernanceState.PEER_REVIEW, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(peerReview));

        RuleGovernance updated = service.recordSignoff(
            "tenant-A",
            "version-1",
            RuleSignoffStage.PEER_REVIEW,
            RuleSignoffDecision.APPROVED,
            "DDI 规则经临床药师复核通过",
            "pharmacist-1",
            RoleCode.MEDICATION_SAFETY_USER,
            "trace-pharmacist");

        assertThat(updated.state()).isEqualTo(RuleGovernanceState.COMMITTEE);
        verify(signoffRepository).save(org.mockito.ArgumentMatchers.argThat(signoff ->
            signoff.signerRole().equals(RoleCode.MEDICATION_SAFETY_USER.code())
                && signoff.signerId().equals("pharmacist-1")
        ));
    }

    @Test
    void mapsRacingDuplicateSignoffToConflict() {
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(committee));
        when(signoffRepository.save(any(RuleSignoff.class)))
            .thenThrow(new DuplicateKeyException("uk_rule_signoff_signer"));

        assertThatThrownBy(() -> service.recordSignoff(
                "tenant-A",
                "version-1",
                RuleSignoffStage.COMMITTEE,
                RuleSignoffDecision.APPROVED,
                "并发签署",
                "signer-1",
                RoleCode.CLINICAL_GOVERNOR,
                "trace-racing"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不能重复会签")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void mapsConcurrentGovernanceUpdateToConflict() {
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(committee));
        when(signoffRepository.findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
            "version-1", "tenant-A"))
            .thenReturn(List.of(
                signoff("signer-1", RuleSignoffStage.COMMITTEE),
                signoff("signer-2", RuleSignoffStage.COMMITTEE)
            ));
        when(governanceRepository.save(any(RuleGovernance.class)))
            .thenThrow(new OptimisticLockingFailureException("stale governance"));

        assertThatThrownBy(() -> service.transition(
                "tenant-A",
                "version-1",
                RuleGovernanceState.SHADOW,
                "会签完成",
                "publisher-1",
                "trace-concurrent"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("状态已变化")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void newReviewRoundCannotReusePriorCommitteeApprovals() {
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE, 2, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(committee));
        when(signoffRepository.findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
            "version-1", "tenant-A"))
            .thenReturn(List.of(
                signoff("signer-1", RuleSignoffStage.COMMITTEE, 1),
                signoff("signer-2", RuleSignoffStage.COMMITTEE, 1)
            ));

        assertThatThrownBy(() -> service.transition(
                "tenant-A",
                "version-1",
                RuleGovernanceState.SHADOW,
                "第二轮进入影子验证",
                "publisher-1",
                "trace-round-2"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("仍需 2 名独立委员会成员会签")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void authorAndCurrentSignersCannotPublishReviewedRule() {
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(committee));
        when(signoffRepository.findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
            "version-1", "tenant-A"))
            .thenReturn(List.of(
                signoff("signer-1", RuleSignoffStage.COMMITTEE),
                signoff("signer-2", RuleSignoffStage.COMMITTEE)
            ));

        assertThatThrownBy(() -> service.transition(
                "tenant-A",
                "version-1",
                RuleGovernanceState.SHADOW,
                "作者尝试发布",
                "author-1",
                "trace-separation"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("作者、会签人和发布人必须相互分离")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void currentCommitteeSignerCannotPublishReviewedRule() {
        RuleGovernance committee = governance(RuleGovernanceState.COMMITTEE, 1);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(committee));
        when(signoffRepository.findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
            "version-1", "tenant-A"))
            .thenReturn(List.of(signoff("signer-1", RuleSignoffStage.COMMITTEE)));

        assertThatThrownBy(() -> service.transition(
                "tenant-A",
                "version-1",
                RuleGovernanceState.SHADOW,
                "会签人尝试发布",
                "signer-1",
                "trace-signer-publish"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("作者、会签人和发布人必须相互分离")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(governanceRepository, never()).save(any());
    }

    @Test
    void retiresMonitoredRuleWithoutDeletingGovernanceEvidence() {
        RuleGovernance monitoring = governance(RuleGovernanceState.MONITOR, 2);
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(monitoring));

        RuleGovernance retired = service.transition(
            "tenant-A",
            "version-1",
            RuleGovernanceState.RETIRED,
            "新版规则已完成替代",
            "publisher-2",
            "trace-6");

        assertThat(retired.state()).isEqualTo(RuleGovernanceState.RETIRED);
        assertThat(retired.updatedBy()).isEqualTo("publisher-2");
        verify(governanceRepository).save(retired);
        verify(signoffRepository, never()).delete(any());
    }

    private RuleGovernance governance(RuleGovernanceState state, int requiredSignoffs) {
        return governance(state, requiredSignoffs, 1);
    }

    private RuleGovernance governance(
            RuleGovernanceState state,
            int requiredSignoffs,
            int reviewRound) {
        Instant now = CLOCK.instant();
        return new RuleGovernance(
            1L,
            "rg-1",
            "tenant-A",
            "version-1",
            state,
            requiredSignoffs,
            reviewRound,
            "author-1",
            "准备治理",
            now,
            "author-1",
            now,
            "author-1",
            "trace-1",
            0L
        );
    }

    private RuleSignoff signoff(String signerId, RuleSignoffStage stage) {
        return signoff(signerId, stage, 1);
    }

    private RuleSignoff signoff(String signerId, RuleSignoffStage stage, int reviewRound) {
        return new RuleSignoff(
            1L,
            "rs-" + signerId,
            "tenant-A",
            "version-1",
            stage,
            reviewRound,
            RoleCode.CLINICAL_GOVERNOR.code(),
            signerId,
            RuleSignoffDecision.APPROVED,
            "同意",
            CLOCK.instant(),
            "trace-signoff"
        );
    }
}
