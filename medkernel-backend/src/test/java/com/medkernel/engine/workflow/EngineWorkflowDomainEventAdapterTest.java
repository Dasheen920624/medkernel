package com.medkernel.engine.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.event.ClockSlaBreachedEvent;
import com.medkernel.engine.event.OverrideCapturedEvent;
import com.medkernel.engine.event.PathwayVarianceRecordedEvent;
import com.medkernel.engine.event.RuleFiredEvent;
import com.medkernel.engine.quality.dashboard.QualityDashboardAlert;
import com.medkernel.engine.quality.dashboard.QualityDashboardAlertRepository;
import com.medkernel.engine.quality.dashboard.QualityDashboardAlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EngineWorkflowDomainEventAdapterTest {

    private WorkflowTodoRepository todos;
    private WorkflowNotificationRepository notifications;
    private QualityDashboardAlertRepository alerts;
    private EngineWorkflowDomainEventAdapter adapter;

    @BeforeEach
    void setUp() {
        todos = mock(WorkflowTodoRepository.class);
        notifications = mock(WorkflowNotificationRepository.class);
        alerts = mock(QualityDashboardAlertRepository.class);
        adapter = new EngineWorkflowDomainEventAdapter(todos, notifications, alerts);
        when(todos.findByTenantIdAndSourceTypeAndSourceId(eq("tenant-A"), any(), any()))
            .thenReturn(Optional.empty());
        when(notifications.findByTenantIdAndDedupeKey(eq("tenant-A"), any()))
            .thenReturn(Optional.empty());
        when(alerts.findByTenantIdAndAlertTypeAndSourceTypeAndSourceId(
            eq("tenant-A"), any(), any(), any()))
            .thenReturn(Optional.empty());
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(alerts.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void ruleFiredCreatesActionNotificationWithPackageVersion() {
        adapter.ruleFired(new RuleFiredEvent(
            "tenant-A", "trace-rule", "rpv-1", "rule-1", "RULE.ANTICOAG", "version-1",
            "rex-1", "order-sign", "evt-1", "patient-1", "enc-1",
            "HIGH", List.of("STRONG_REMINDER"), Instant.parse("2026-06-08T08:00:00Z")));

        ArgumentCaptor<WorkflowNotification> notificationCap =
            ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications).save(notificationCap.capture());
        assertThat(notificationCap.getValue().sourceType()).isEqualTo(WorkflowNotificationSourceType.RULE_EVENT);
        assertThat(notificationCap.getValue().sourceId()).isEqualTo("rex-1");
        assertThat(notificationCap.getValue().dedupeKey()).isEqualTo("rule-fired:rex-1");
        assertThat(notificationCap.getValue().message()).contains("rpv-1", "STRONG_REMINDER");
        assertThat(notificationCap.getValue().patientId()).isEqualTo("patient-1");
    }

    @Test
    void overrideCapturedCreatesQualityAlertWithExecutionEvidence() {
        adapter.overrideCaptured(new OverrideCapturedEvent(
            "tenant-A", "trace-rule", "rpv-1", "rov-1", "rex-1",
            "rule-1", "RULE.ANTICOAG", "version-1", "patient-1", "enc-1",
            "BLOCK", "已完成临床复核", "doctor-1", Instant.parse("2026-06-08T08:05:00Z")));

        ArgumentCaptor<QualityDashboardAlert> alertCap =
            ArgumentCaptor.forClass(QualityDashboardAlert.class);
        verify(alerts).save(alertCap.capture());
        assertThat(alertCap.getValue().alertType()).isEqualTo(QualityDashboardAlertType.RULE_OVERRIDE);
        assertThat(alertCap.getValue().sourceType()).isEqualTo("rule_override_log");
        assertThat(alertCap.getValue().sourceId()).isEqualTo("rov-1");
        assertThat(alertCap.getValue().severity()).isEqualTo("P0");
        assertThat(alertCap.getValue().evidenceSummary()).contains("rex-1", "rpv-1", "BLOCK");
    }

    @Test
    void pathwayVarianceCreatesTodoAndNotification() {
        adapter.pathwayVarianceRecorded(new PathwayVarianceRecordedEvent(
            "tenant-A", "trace-pathway", "pkg-2026.06", "pp-1", "patient-1", "enc-1",
            "pv-1", "ASSESS", "CLINICAL", "CLINICAL_ESCALATION", "主管医师",
            "HOLD", Instant.parse("2026-06-08T08:10:00Z")));

        ArgumentCaptor<WorkflowTodo> todoCap = ArgumentCaptor.forClass(WorkflowTodo.class);
        verify(todos).save(todoCap.capture());
        assertThat(todoCap.getValue().sourceType()).isEqualTo(WorkflowTodoSourceType.PATHWAY_EVENT);
        assertThat(todoCap.getValue().sourceId()).isEqualTo("pv-1");
        assertThat(todoCap.getValue().assigneeRole()).isEqualTo("主管医师");
        assertThat(todoCap.getValue().summary()).contains("pkg-2026.06", "CLINICAL_ESCALATION");

        ArgumentCaptor<WorkflowNotification> notificationCap =
            ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications).save(notificationCap.capture());
        assertThat(notificationCap.getValue().sourceType()).isEqualTo(WorkflowNotificationSourceType.PATHWAY_EVENT);
        assertThat(notificationCap.getValue().dedupeKey()).isEqualTo("pathway-variance:pv-1");

        ArgumentCaptor<QualityDashboardAlert> alertCap =
            ArgumentCaptor.forClass(QualityDashboardAlert.class);
        verify(alerts).save(alertCap.capture());
        assertThat(alertCap.getValue().alertType()).isEqualTo(QualityDashboardAlertType.PATHWAY_VARIANCE);
        assertThat(alertCap.getValue().sourceType()).isEqualTo("pathway_variance");
        assertThat(alertCap.getValue().sourceId()).isEqualTo("pv-1");
        assertThat(alertCap.getValue().severity()).isEqualTo("P2");
        assertThat(alertCap.getValue().thresholdValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(alertCap.getValue().actualValue()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(alertCap.getValue().evidenceSummary()).contains("pkg-2026.06", "HOLD");
    }

    @Test
    void clockSlaBreachedCreatesTodoNotificationAndQualityAlert() {
        adapter.clockSlaBreached(new ClockSlaBreachedEvent(
            "tenant-A", "trace-pathway", "pkg-2026.06", "pp-1", "patient-1", "enc-1",
            "clock-1", "ABX", "COPD.TIME_TO_FOLLOWUP", "QUALITY_RECORD",
            Instant.parse("2026-06-08T07:30:00Z"), Instant.parse("2026-06-08T08:30:00Z")));

        ArgumentCaptor<WorkflowTodo> todoCap = ArgumentCaptor.forClass(WorkflowTodo.class);
        verify(todos).save(todoCap.capture());
        assertThat(todoCap.getValue().sourceType()).isEqualTo(WorkflowTodoSourceType.PATHWAY_EVENT);
        assertThat(todoCap.getValue().sourceId()).isEqualTo("clock-sla:clock-1");
        assertThat(todoCap.getValue().priority()).isEqualTo(WorkflowPriority.CRITICAL);

        ArgumentCaptor<QualityDashboardAlert> alertCap =
            ArgumentCaptor.forClass(QualityDashboardAlert.class);
        verify(alerts).save(alertCap.capture());
        assertThat(alertCap.getValue().alertType()).isEqualTo(QualityDashboardAlertType.CLOCK_SLA_BREACH);
        assertThat(alertCap.getValue().sourceType()).isEqualTo("clinical_clock");
        assertThat(alertCap.getValue().sourceId()).isEqualTo("clock-1");
        assertThat(alertCap.getValue().severity()).isEqualTo("P1");
        assertThat(alertCap.getValue().evidenceSummary()).contains("pkg-2026.06", "QUALITY_RECORD");
    }

    @Test
    void pathwayVarianceReplayDoesNotDuplicateTodoNotificationOrQualityAlert() {
        when(todos.findByTenantIdAndSourceTypeAndSourceId(
                "tenant-A", WorkflowTodoSourceType.PATHWAY_EVENT, "pv-1"))
            .thenReturn(Optional.of(existingTodo()));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "pathway-variance:pv-1"))
            .thenReturn(Optional.of(existingNotification()));
        when(alerts.findByTenantIdAndAlertTypeAndSourceTypeAndSourceId(
                "tenant-A", QualityDashboardAlertType.PATHWAY_VARIANCE, "pathway_variance", "pv-1"))
            .thenReturn(Optional.of(existingAlert()));

        adapter.pathwayVarianceRecorded(new PathwayVarianceRecordedEvent(
            "tenant-A", "trace-pathway", "pkg-2026.06", "pp-1", "patient-1", "enc-1",
            "pv-1", "ASSESS", "CLINICAL", "CLINICAL_ESCALATION", "主管医师",
            "HOLD", Instant.parse("2026-06-08T08:10:00Z")));

        verify(todos, never()).save(any());
        verify(notifications, never()).save(any());
        ArgumentCaptor<QualityDashboardAlert> alertCap = ArgumentCaptor.forClass(QualityDashboardAlert.class);
        verify(alerts).save(alertCap.capture());
        assertThat(alertCap.getValue().id()).isEqualTo(1L);
        assertThat(alertCap.getValue().alertId()).isEqualTo("qalert-existing");
    }

    private WorkflowTodo existingTodo() {
        Instant now = Instant.parse("2026-06-08T08:00:00Z");
        return new WorkflowTodo(
            1L,
            "todo-existing",
            "tenant-A",
            WorkflowTodoSourceType.PATHWAY_EVENT,
            "pv-1",
            "路径变异待处理",
            "已存在",
            WorkflowPriority.HIGH,
            WorkflowTodoStatus.PENDING,
            null,
            "主管医师",
            "patient-1",
            "enc-1",
            null,
            "/clinical/pathways?varianceId=pv-1",
            null,
            null,
            null,
            null,
            null,
            "trace-pathway",
            now,
            "engine-event",
            now,
            "engine-event");
    }

    private WorkflowNotification existingNotification() {
        Instant now = Instant.parse("2026-06-08T08:00:00Z");
        return new WorkflowNotification(
            1L,
            "notify-existing",
            "tenant-A",
            WorkflowNotificationSourceType.PATHWAY_EVENT,
            "pv-1",
            "pathway-variance:pv-1",
            "路径变异已登记",
            "已存在",
            WorkflowNotificationLevel.HIGH,
            WorkflowNotificationStatus.UNREAD,
            null,
            "主管医师",
            "patient-1",
            "enc-1",
            "/clinical/pathways?varianceId=pv-1",
            null,
            null,
            "trace-pathway",
            now,
            "engine-event",
            now,
            "engine-event");
    }

    private QualityDashboardAlert existingAlert() {
        Instant now = Instant.parse("2026-06-08T08:00:00Z");
        return new QualityDashboardAlert(
            1L,
            "qalert-existing",
            "tenant-A",
            null,
            QualityDashboardAlertType.PATHWAY_VARIANCE,
            "pathway_variance",
            "pv-1",
            "P2",
            com.medkernel.engine.quality.dashboard.QualityDashboardAlertStatus.OPEN,
            "PATHWAY_VARIANCE",
            null,
            null,
            "路径变异已登记",
            "已存在",
            now,
            "engine-event",
            now,
            "engine-event",
            "trace-pathway");
    }
}
