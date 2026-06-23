package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class RuleGovernanceServiceTest {

    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-06-07T13:30:00Z"), ZoneOffset.UTC);

    private RuleGovernanceRepository governanceRepository;
    private RuleGovernanceService service;

    @BeforeEach
    void setUp() {
        governanceRepository = mock(RuleGovernanceRepository.class);
        when(governanceRepository.save(any(RuleGovernance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        service = new RuleGovernanceService(governanceRepository, CLOCK);
    }

    @Test
    void initializesEveryRiskLevelAsAPlainDraftWithoutSignoffCounters() {
        RuleGovernance governance = service.initialize(
            "tenant-A", "version-1", RuleRiskLevel.HIGH, "operator-1", "trace-1");

        assertThat(governance.state()).isEqualTo(RuleGovernanceState.DRAFT);
        assertThat(governance.authorId()).isEqualTo("operator-1");
        assertThat(governance.lastReason()).isEqualTo("规则草稿已创建");
        verify(governanceRepository).save(governance);
    }

    @Test
    void sameResponsibleOperatorCanConfirmDraftAndContinueToShadow() {
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(
                Optional.of(governance(RuleGovernanceState.DRAFT)),
                Optional.of(governance(RuleGovernanceState.REVIEWED))
            );

        RuleGovernance reviewed = service.transition(
            "tenant-A",
            "version-1",
            RuleGovernanceState.REVIEWED,
            "测试、术语和影响分析均已确认",
            "operator-1",
            "trace-2"
        );
        RuleGovernance shadow = service.transition(
            "tenant-A",
            "version-1",
            RuleGovernanceState.SHADOW,
            "进入影子验证",
            "operator-1",
            "trace-3"
        );

        assertThat(reviewed.state()).isEqualTo(RuleGovernanceState.REVIEWED);
        assertThat(reviewed.updatedBy()).isEqualTo("operator-1");
        assertThat(shadow.state()).isEqualTo(RuleGovernanceState.SHADOW);
    }

    @Test
    void requiresTheClosedLifecycleWithoutSkippingTechnicalStages() {
        assertInvalidTransition(RuleGovernanceState.DRAFT, RuleGovernanceState.SHADOW);
        assertInvalidTransition(RuleGovernanceState.REVIEWED, RuleGovernanceState.CANARY);
        assertInvalidTransition(RuleGovernanceState.SHADOW, RuleGovernanceState.FULL);
        assertInvalidTransition(RuleGovernanceState.CANARY, RuleGovernanceState.MONITOR);
        assertInvalidTransition(RuleGovernanceState.FULL, RuleGovernanceState.RETIRED);
    }

    @Test
    void advancesThroughShadowCanaryFullMonitorAndRetired() {
        assertTransition(RuleGovernanceState.SHADOW, RuleGovernanceState.CANARY);
        assertTransition(RuleGovernanceState.CANARY, RuleGovernanceState.FULL);
        assertTransition(RuleGovernanceState.FULL, RuleGovernanceState.MONITOR);
        assertTransition(RuleGovernanceState.MONITOR, RuleGovernanceState.RETIRED);
    }

    @Test
    void rejectsFurtherTransitionAfterRetirement() {
        assertInvalidTransition(RuleGovernanceState.RETIRED, RuleGovernanceState.DRAFT);
    }

    @Test
    void mapsConcurrentGovernanceUpdateToConflict() {
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(governance(RuleGovernanceState.REVIEWED)));
        when(governanceRepository.save(any(RuleGovernance.class)))
            .thenThrow(new OptimisticLockingFailureException("stale governance"));

        assertThatThrownBy(() -> service.transition(
                "tenant-A",
                "version-1",
                RuleGovernanceState.SHADOW,
                "进入影子验证",
                "operator-1",
                "trace-concurrent"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("状态已变化")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    private void assertTransition(RuleGovernanceState source, RuleGovernanceState target) {
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(governance(source)));

        RuleGovernance updated = service.transition(
            "tenant-A",
            "version-1",
            target,
            "推进至 " + target,
            "operator-1",
            "trace-transition"
        );

        assertThat(updated.state()).isEqualTo(target);
        assertThat(updated.updatedBy()).isEqualTo("operator-1");
    }

    private void assertInvalidTransition(
            RuleGovernanceState source,
            RuleGovernanceState target) {
        when(governanceRepository.findByRuleVersionIdAndTenantId("version-1", "tenant-A"))
            .thenReturn(Optional.of(governance(source)));

        assertThatThrownBy(() -> service.transition(
                "tenant-A",
                "version-1",
                target,
                "尝试跳级",
                "operator-1",
                "trace-invalid"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("非法治理状态迁移")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    private RuleGovernance governance(RuleGovernanceState state) {
        Instant now = CLOCK.instant();
        return new RuleGovernance(
            1L,
            "rg-1",
            "tenant-A",
            "version-1",
            state,
            "operator-1",
            "准备治理",
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-1",
            0L
        );
    }
}
