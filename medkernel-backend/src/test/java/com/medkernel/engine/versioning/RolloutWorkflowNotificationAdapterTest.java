package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.workflow.WorkflowNotification;
import com.medkernel.engine.workflow.WorkflowNotificationLevel;
import com.medkernel.engine.workflow.WorkflowNotificationRepository;
import com.medkernel.engine.workflow.WorkflowNotificationSourceType;
import com.medkernel.engine.workflow.WorkflowNotificationStatus;

class RolloutWorkflowNotificationAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-07T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsDeduplicatedHighPriorityNotificationForPausedRollout() {
        WorkflowNotificationRepository notifications = mock(WorkflowNotificationRepository.class);
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "rollout-paused:vrl-1:1"))
            .thenReturn(Optional.empty(), Optional.of(mock(WorkflowNotification.class)));
        RolloutWorkflowNotificationAdapter adapter =
            new RolloutWorkflowNotificationAdapter(notifications, CLOCK);
        VersionReleasePlan plan = pausedPlan();

        adapter.notifyPaused(plan, "异常率 0.1 > 0.05");
        adapter.notifyPaused(plan, "异常率 0.1 > 0.05");

        ArgumentCaptor<WorkflowNotification> captor = ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications).save(captor.capture());
        WorkflowNotification saved = captor.getValue();
        assertThat(saved.sourceType()).isEqualTo(WorkflowNotificationSourceType.RELEASE_ROLLOUT);
        assertThat(saved.level()).isEqualTo(WorkflowNotificationLevel.HIGH);
        assertThat(saved.status()).isEqualTo(WorkflowNotificationStatus.UNREAD);
        assertThat(saved.recipientRole()).isEqualTo("ENGINE_OPERATOR");
        assertThat(saved.deepLink()).contains("releasePlanId=vrl-1");
    }

    private VersionReleasePlan pausedPlan() {
        Instant now = CLOCK.instant();
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
            "{}",
            1,
            "异常率 0.1 > 0.05",
            VersionReleaseStatus.PAUSED,
            "impact",
            "review",
            "evidence",
            null,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace-rollout"
        );
    }
}
