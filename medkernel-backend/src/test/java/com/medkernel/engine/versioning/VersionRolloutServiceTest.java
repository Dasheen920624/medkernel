package com.medkernel.engine.versioning;

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

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class VersionRolloutServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-06-07T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-07T10:00:00Z"), ZoneOffset.UTC);

    private VersionReleasePlanRepository releasePlans;
    private VersionRolloutObservationRepository observations;
    private RolloutPauseNotifier pauseNotifier;
    private VersionRolloutService service;

    @BeforeEach
    void setUp() {
        releasePlans = mock(VersionReleasePlanRepository.class);
        observations = mock(VersionRolloutObservationRepository.class);
        pauseNotifier = mock(RolloutPauseNotifier.class);
        service = new VersionRolloutService(releasePlans, observations, pauseNotifier, CLOCK);
        when(observations.save(any(VersionRolloutObservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(releasePlans.save(any(VersionReleasePlan.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void pausesStagedRolloutAndNotifiesWhenAnomalyRateExceedsThreshold() {
        VersionReleasePlan plan = stagedPlan(0, VersionReleaseStatus.GRAY);
        when(releasePlans.findByPlanIdAndTenantId("vrl-1", "tenant-A"))
            .thenReturn(Optional.of(plan));

        VersionRolloutObservationResult result = service.observe(command(0, 100, 30, 10));

        assertThat(result.paused()).isTrue();
        assertThat(result.readyForFullRelease()).isFalse();
        assertThat(result.currentStagePercent()).isEqualTo(5);
        assertThat(result.observation().anomalyRate()).isEqualByComparingTo("0.100000");
        assertThat(result.plan().status()).isEqualTo(VersionReleaseStatus.PAUSED);
        assertThat(result.plan().rolloutStageIndex()).isZero();
        assertThat(result.plan().rolloutPausedReason()).contains("异常率");
        verify(pauseNotifier).notifyPaused(result.plan(), result.plan().rolloutPausedReason());
    }

    @Test
    void advancesToNextStageOnlyAfterObservationWindowWithoutThresholdBreach() {
        VersionReleasePlan plan = stagedPlan(0, VersionReleaseStatus.GRAY);
        when(releasePlans.findByPlanIdAndTenantId("vrl-1", "tenant-A"))
            .thenReturn(Optional.of(plan));

        VersionRolloutObservationResult result = service.observe(command(0, 100, 30, 1));

        assertThat(result.paused()).isFalse();
        assertThat(result.readyForFullRelease()).isFalse();
        assertThat(result.currentStagePercent()).isEqualTo(25);
        assertThat(result.plan().rolloutStageIndex()).isEqualTo(1);
        assertThat(result.plan().status()).isEqualTo(VersionReleaseStatus.GRAY);
        verify(pauseNotifier, never()).notifyPaused(any(), any());
    }

    @Test
    void rejectsObservationForStaleStageWithoutWritingFact() {
        VersionReleasePlan plan = stagedPlan(1, VersionReleaseStatus.GRAY);
        when(releasePlans.findByPlanIdAndTenantId("vrl-1", "tenant-A"))
            .thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.observe(command(0, 100, 30, 1)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("批次")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(observations, never()).save(any());
        verify(releasePlans, never()).save(any());
    }

    @Test
    void rollsBackGrayPlanToItsRecordedPreviousPin() {
        VersionReleasePlan plan = stagedPlan(1, VersionReleaseStatus.PAUSED);
        when(releasePlans.findByPlanIdAndTenantId("vrl-1", "tenant-A"))
            .thenReturn(Optional.of(plan));

        VersionReleasePlan rolledBack = service.rollback(new VersionRolloutRollbackCommand(
            "tenant-A",
            "vrl-1",
            "灰度异常，恢复上一钉点",
            true,
            "operator-2",
            "trace-rollback"
        ));

        assertThat(rolledBack.status()).isEqualTo(VersionReleaseStatus.ROLLED_BACK);
        assertThat(rolledBack.fromVersionId()).isEqualTo("av-v1");
        assertThat(rolledBack.evidenceSummary())
            .contains("av-v1")
            .contains("灰度异常，恢复上一钉点");
        verify(releasePlans).save(any(VersionReleasePlan.class));
    }

    private VersionRolloutObservationCommand command(
            int stageIndex,
            long sampleCount,
            long manualRejections,
            long anomalies) {
        return new VersionRolloutObservationCommand(
            "tenant-A",
            "vrl-1",
            stageIndex,
            sampleCount,
            40L,
            20L,
            manualRejections,
            anomalies,
            CLOCK.instant(),
            "operator-1",
            "trace-rollout"
        );
    }

    private VersionReleasePlan stagedPlan(int stageIndex, VersionReleaseStatus status) {
        RolloutPolicy policy = new RolloutPolicy(
            RolloutStrategy.STAGED,
            List.of(),
            null,
            List.of(5, 25, 100),
            30,
            new RolloutThresholds(0.8, 0.5, 0.4, 0.05)
        );
        return new VersionReleasePlan(
            1L,
            "vrl-1",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v2",
            "av-v1",
            "/TENANT-A/HOSP-A",
            "adult|inpatient",
            VersionReleaseScopeType.FACILITY,
            "/TENANT-A/HOSP-A",
            RolloutStrategy.STAGED,
            RolloutPolicyJson.encode(policy),
            stageIndex,
            null,
            status,
            "impact-digest",
            "已审核",
            "灰度发布",
            null,
            BASE_TIME,
            "operator-1",
            BASE_TIME,
            "operator-1",
            "trace-rollout"
        );
    }
}
