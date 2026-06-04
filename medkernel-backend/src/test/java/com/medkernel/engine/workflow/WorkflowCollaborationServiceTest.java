package com.medkernel.engine.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.dto.IntegrationOutboundResultDto;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.engine.context.ClinicalEvent;
import com.medkernel.engine.context.ClinicalEventRepository;
import com.medkernel.engine.context.ClinicalEventStatus;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.followup.FollowupEventRepository;
import com.medkernel.engine.followup.FollowupTaskRepository;
import com.medkernel.engine.followup.FollowupTaskStatus;
import com.medkernel.engine.followup.FollowupTaskType;
import com.medkernel.engine.knowledge.AffectedCaseTargetType;
import com.medkernel.engine.knowledge.AffectedCaseTask;
import com.medkernel.engine.knowledge.AffectedCaseTaskRepository;
import com.medkernel.engine.knowledge.AffectedCaseTaskStatus;
import com.medkernel.engine.knowledge.AffectedCaseTaskType;
import com.medkernel.engine.recommendation.RecommendationCardRepository;
import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationWorkflowTodoRow;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowCollaborationServiceTest {

    private WorkflowTodoRepository todos;
    private WorkflowNotificationRepository notifications;
    private FollowupTaskRepository followupTasks;
    private FollowupEventRepository followupEvents;
    private AffectedCaseTaskRepository affectedTasks;
    private RecommendationCardRepository recommendationCards;
    private ClinicalEventRepository clinicalEvents;
    private IntegrationService integrationService;
    private WorkflowNotificationSettingsService notificationSettings;
    private WorkflowCollaborationService service;

    @BeforeEach
    void setUp() {
        todos = mock(WorkflowTodoRepository.class);
        notifications = mock(WorkflowNotificationRepository.class);
        followupTasks = mock(FollowupTaskRepository.class);
        followupEvents = mock(FollowupEventRepository.class);
        affectedTasks = mock(AffectedCaseTaskRepository.class);
        recommendationCards = mock(RecommendationCardRepository.class);
        clinicalEvents = mock(ClinicalEventRepository.class);
        integrationService = mock(IntegrationService.class);
        notificationSettings = mock(WorkflowNotificationSettingsService.class);
        service = new WorkflowCollaborationService(
            todos,
            notifications,
            followupTasks,
            followupEvents,
            affectedTasks,
            recommendationCards,
            clinicalEvents,
            integrationService,
            notificationSettings);
        RequestContext.restore(new RequestContext.Snapshot("trace-workflow", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(recommendationCards.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of());
        when(clinicalEvents.pageByFilter("tenant-A", null, null, ClinicalEventStatus.PROCESSED.name(), null, 0, 200))
            .thenReturn(List.of());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(integrationService.enqueueOutboundMessage(eq("tenant-A"), any(IntegrationOutboundRequestDto.class)))
            .thenAnswer(inv -> outboundResult(inv.getArgument(1)));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void listTodosProjectsRealFollowupAndSafetyTasksWithoutFabricatingBrowserRows() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        when(followupTasks.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of(
            new FollowupWorkflowTodoRow(
                "ft-return-1",
                "fp-1",
                FollowupTaskType.RETURN_VISIT,
                FollowupTaskStatus.ABNORMAL_RETURN,
                "patient-1",
                "enc-1",
                now.plusSeconds(3600),
                "followup-doctor",
                "DOCTOR",
                "trace-followup",
                now)));
        when(affectedTasks.pageByTenantId("tenant-A", 0, 200)).thenReturn(List.of(
            new AffectedCaseTask(
                null,
                "tenant-A",
                "withdrawal:patient-1",
                90L,
                10L,
                100L,
                AffectedCaseTaskType.PHYSICIAN_REVIEW,
                AffectedCaseTaskStatus.OPEN,
                AffectedCaseTargetType.PATIENT_CASE,
                "patient-1",
                "安全撤回后需医师复核患者病例",
                now.plusSeconds(1800),
                "doctor-1",
                "trace-safety",
                now,
                "system",
                now,
                "system")));
        when(todos.findByTenantIdAndSourceTypeAndSourceId(eq("tenant-A"), any(), any()))
            .thenReturn(Optional.empty());
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(todos.countByFilter("tenant-A", null, null, null, null, null)).thenReturn(2L);
        when(todos.pageByFilter("tenant-A", null, null, null, null, null, 0, 20)).thenReturn(List.of(
            new WorkflowTodo(
                null,
                "todo-followup-1",
                "tenant-A",
                WorkflowTodoSourceType.FOLLOWUP_TASK,
                "ft-return-1",
                "随访异常返院任务",
                "患者随访异常，需要安排回院确认",
                WorkflowPriority.HIGH,
                WorkflowTodoStatus.PENDING,
                "followup-doctor",
                "DOCTOR",
                "patient-1",
                "enc-1",
                now.plusSeconds(3600),
                "/clinical/followup?taskId=ft-return-1",
                null,
                null,
                null,
                null,
                null,
                "trace-followup",
                now,
                "system",
                now,
                "system"),
            new WorkflowTodo(
                null,
                "todo-safety-1",
                "tenant-A",
                WorkflowTodoSourceType.SAFETY_REVIEW,
                "withdrawal:patient-1",
                "安全撤回复核任务",
                "安全撤回后需医师复核患者病例",
                WorkflowPriority.CRITICAL,
                WorkflowTodoStatus.PENDING,
                "doctor-1",
                "DOCTOR",
                "patient-1",
                null,
                now.plusSeconds(1800),
                "/provenance?taskKey=withdrawal:patient-1",
                null,
                null,
                null,
                null,
                null,
                "trace-safety",
                now,
                "system",
                now,
                "system")));

        PageResponse<WorkflowTodoResponse> page = service.listTodos(
            new WorkflowTodoFilter(null, null, null, null, null),
            new PageRequest(1, 20, null));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(WorkflowTodoResponse::sourceType)
            .containsExactly(WorkflowTodoSourceType.FOLLOWUP_TASK, WorkflowTodoSourceType.SAFETY_REVIEW);
        assertThat(page.items()).extracting(WorkflowTodoResponse::patientId)
            .containsExactly("patient-1", "patient-1");
        verify(todos, times(2)).save(any(WorkflowTodo.class));
    }

    @Test
    void completeTodoPersistsAuditableClosureInsteadOfOnlyChangingBrowserState() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo pending = new WorkflowTodo(
            null,
            "todo-safety-1",
            "tenant-A",
            WorkflowTodoSourceType.SAFETY_REVIEW,
            "withdrawal:patient-1",
            "安全撤回复核任务",
            "安全撤回后需医师复核患者病例",
            WorkflowPriority.CRITICAL,
            WorkflowTodoStatus.PENDING,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            null,
            now.plusSeconds(1800),
            "/provenance?taskKey=withdrawal:patient-1",
            null,
            null,
            null,
            null,
            null,
            "trace-safety",
            now,
            "system",
            now,
            "system");
        when(todos.findByTenantIdAndTodoId("tenant-A", "todo-safety-1")).thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowTodoResponse completed = service.completeTodo(
            "todo-safety-1",
            new WorkflowTodoCompleteRequest("已复核患者病例，未发现仍在执行的旧版医嘱"));

        assertThat(completed.status()).isEqualTo(WorkflowTodoStatus.COMPLETED);
        assertThat(completed.completedBy()).isEqualTo("doctor-1");
        assertThat(completed.completionReason()).contains("已复核患者病例");
        verify(todos).save(any(WorkflowTodo.class));
    }

    @Test
    void completeTodoCreatesLowDisturbanceWorkflowNotificationForCurrentAssignee() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo pending = new WorkflowTodo(
            null,
            "todo-safety-1",
            "tenant-A",
            WorkflowTodoSourceType.SAFETY_REVIEW,
            "withdrawal:patient-1",
            "安全撤回复核任务",
            "安全撤回后需医师复核患者病例",
            WorkflowPriority.CRITICAL,
            WorkflowTodoStatus.PENDING,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            null,
            now.plusSeconds(1800),
            "/provenance?taskKey=withdrawal:patient-1",
            null,
            null,
            null,
            null,
            null,
            "trace-safety",
            now,
            "system",
            now,
            "system");
        when(todos.findByTenantIdAndTodoId("tenant-A", "todo-safety-1")).thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "todo:todo-safety-1:completed"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.completeTodo(
            "todo-safety-1",
            new WorkflowTodoCompleteRequest("已复核患者病例，未发现仍在执行的旧版医嘱"));

        ArgumentCaptor<WorkflowNotification> notificationCaptor =
            ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications).save(notificationCaptor.capture());
        WorkflowNotification notification = notificationCaptor.getValue();
        assertThat(notification.sourceType()).isEqualTo(WorkflowNotificationSourceType.WORKFLOW_TODO);
        assertThat(notification.sourceId()).isEqualTo("todo-safety-1");
        assertThat(notification.dedupeKey()).isEqualTo("todo:todo-safety-1:completed");
        assertThat(notification.title()).isEqualTo("待办已完成");
        assertThat(notification.message()).contains("安全撤回复核任务", "已复核患者病例");
        assertThat(notification.level()).isEqualTo(WorkflowNotificationLevel.INFO);
        assertThat(notification.status()).isEqualTo(WorkflowNotificationStatus.UNREAD);
        assertThat(notification.recipientId()).isEqualTo("doctor-1");
        assertThat(notification.recipientRole()).isEqualTo("DOCTOR");
        assertThat(notification.traceId()).isEqualTo("trace-workflow");
    }

    @Test
    void completeTodoQueuesExternalNotificationCompensationWhenRecipientEnablesChannels() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo pending = new WorkflowTodo(
            null,
            "todo-safety-1",
            "tenant-A",
            WorkflowTodoSourceType.SAFETY_REVIEW,
            "withdrawal:patient-1",
            "安全撤回复核任务",
            "安全撤回后需医师复核患者病例",
            WorkflowPriority.CRITICAL,
            WorkflowTodoStatus.PENDING,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            null,
            now.plusSeconds(1800),
            "/provenance?taskKey=withdrawal:patient-1",
            null,
            null,
            null,
            null,
            null,
            "trace-safety",
            now,
            "system",
            now,
            "system");
        when(todos.findByTenantIdAndTodoId("tenant-A", "todo-safety-1")).thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "todo:todo-safety-1:completed"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        WorkflowNotificationSettingsResponse settings = externalSettings(true, true, false, false);
        when(notificationSettings.getSettingsForUser("tenant-A", "doctor-1")).thenReturn(settings);
        when(notificationSettings.isMutedByQuietHours(eq(WorkflowNotificationLevel.INFO), eq(settings), any(LocalTime.class)))
            .thenReturn(false);

        service.completeTodo(
            "todo-safety-1",
            new WorkflowTodoCompleteRequest("已复核患者病例，未发现仍在执行的旧版医嘱"));

        ArgumentCaptor<IntegrationOutboundRequestDto> outboundCaptor =
            ArgumentCaptor.forClass(IntegrationOutboundRequestDto.class);
        verify(integrationService, times(2)).enqueueOutboundMessage(eq("tenant-A"), outboundCaptor.capture());
        assertThat(outboundCaptor.getAllValues()).extracting(IntegrationOutboundRequestDto::adapterId)
            .containsExactly("notification-sms", "notification-email");
        assertThat(outboundCaptor.getAllValues()).extracting(IntegrationOutboundRequestDto::protocolType)
            .containsExactly("SMS", "EMAIL");
        assertThat(outboundCaptor.getAllValues()).extracting(IntegrationOutboundRequestDto::messageId)
            .allSatisfy(messageId -> assertThat(messageId).startsWith("notify-out-").hasSizeLessThanOrEqualTo(64));
        assertThat(outboundCaptor.getAllValues()).allSatisfy(request -> {
            assertThat(request.traceId()).isEqualTo("trace-workflow");
            assertThat(request.payload().path("recipientId").asText()).isEqualTo("doctor-1");
            assertThat(request.payload().path("sourceType").asText()).isEqualTo("WORKFLOW_TODO");
            assertThat(request.payload().path("sourceId").asText()).isEqualTo("todo-safety-1");
            assertThat(request.payload().path("deepLink").asText()).isEqualTo("/provenance?taskKey=withdrawal:patient-1");
            assertThat(request.payload().has("patientId")).isFalse();
            assertThat(request.payload().has("message")).isFalse();
        });
    }

    @Test
    void completeTodoSkipsExternalNotificationCompensationWhenQuietHoursMuteInfoLevel() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo pending = new WorkflowTodo(
            null,
            "todo-low-1",
            "tenant-A",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "return-task-1",
            "随访任务待处理",
            "患者随访需要处理",
            WorkflowPriority.MEDIUM,
            WorkflowTodoStatus.PENDING,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            "enc-1",
            now.plusSeconds(1800),
            "/clinical/followup?taskId=return-task-1",
            null,
            null,
            null,
            null,
            null,
            "trace-followup",
            now,
            "system",
            now,
            "system");
        when(todos.findByTenantIdAndTodoId("tenant-A", "todo-low-1")).thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "todo:todo-low-1:completed"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        WorkflowNotificationSettingsResponse settings = externalSettings(true, true, false, true);
        when(notificationSettings.getSettingsForUser("tenant-A", "doctor-1")).thenReturn(settings);
        when(notificationSettings.isMutedByQuietHours(eq(WorkflowNotificationLevel.INFO), eq(settings), any(LocalTime.class)))
            .thenReturn(true);

        service.completeTodo(
            "todo-low-1",
            new WorkflowTodoCompleteRequest("已联系患者并完成随访记录"));

        verify(integrationService, never()).enqueueOutboundMessage(eq("tenant-A"), any(IntegrationOutboundRequestDto.class));
    }

    @Test
    void transferTodoPersistsNewAssigneeReasonAndAuditTrace() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo pending = new WorkflowTodo(
            null,
            "todo-followup-1",
            "tenant-A",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "return-task-1",
            "随访异常返院任务",
            "患者随访异常，需要安排回院确认",
            WorkflowPriority.HIGH,
            WorkflowTodoStatus.PENDING,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            "enc-1",
            now.plusSeconds(1800),
            "/clinical/followup?taskId=return-task-1",
            null,
            null,
            null,
            null,
            null,
            "trace-followup",
            now,
            "system",
            now,
            "system");
        when(todos.findByTenantIdAndTodoId("tenant-A", "todo-followup-1")).thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowTodoResponse transferred = service.transferTodo(
            "todo-followup-1",
            new WorkflowTodoTransferRequest("nurse-2", "NURSING", "交由护理站安排回院确认"));

        assertThat(transferred.status()).isEqualTo(WorkflowTodoStatus.TRANSFERRED);
        assertThat(transferred.assigneeId()).isEqualTo("nurse-2");
        assertThat(transferred.assigneeRole()).isEqualTo("NURSING");
        assertThat(transferred.transferredTo()).isEqualTo("nurse-2");
        assertThat(transferred.transferReason()).isEqualTo("交由护理站安排回院确认");
        assertThat(transferred.traceId()).isEqualTo("trace-workflow");
        verify(todos).save(any(WorkflowTodo.class));
    }

    @Test
    void transferTodoRejectsEmptyRequestBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.transferTodo("todo-followup-1", null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("接收人不能为空");
    }

    @Test
    void transferTodoCreatesDeduplicatedWorkflowNotificationForNewAssignee() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo pending = new WorkflowTodo(
            null,
            "todo-followup-1",
            "tenant-A",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "return-task-1",
            "随访异常返院任务",
            "患者随访异常，需要安排回院确认",
            WorkflowPriority.HIGH,
            WorkflowTodoStatus.PENDING,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            "enc-1",
            now.plusSeconds(1800),
            "/clinical/followup?taskId=return-task-1",
            null,
            null,
            null,
            null,
            null,
            "trace-followup",
            now,
            "system",
            now,
            "system");
        when(todos.findByTenantIdAndTodoId("tenant-A", "todo-followup-1")).thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.findByTenantIdAndDedupeKey(
                "tenant-A",
                "todo:todo-followup-1:transferred:nurse-2"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transferTodo(
            "todo-followup-1",
            new WorkflowTodoTransferRequest("nurse-2", "NURSING", "交由护理站安排回院确认"));

        ArgumentCaptor<WorkflowNotification> notificationCaptor =
            ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications).save(notificationCaptor.capture());
        WorkflowNotification notification = notificationCaptor.getValue();
        assertThat(notification.sourceType()).isEqualTo(WorkflowNotificationSourceType.WORKFLOW_TODO);
        assertThat(notification.sourceId()).isEqualTo("todo-followup-1");
        assertThat(notification.dedupeKey()).isEqualTo("todo:todo-followup-1:transferred:nurse-2");
        assertThat(notification.title()).isEqualTo("待办已转交");
        assertThat(notification.message()).contains("随访异常返院任务", "交由护理站安排回院确认");
        assertThat(notification.level()).isEqualTo(WorkflowNotificationLevel.HIGH);
        assertThat(notification.status()).isEqualTo(WorkflowNotificationStatus.UNREAD);
        assertThat(notification.recipientId()).isEqualTo("nurse-2");
        assertThat(notification.recipientRole()).isEqualTo("NURSING");
        assertThat(notification.deepLink()).isEqualTo("/clinical/followup?taskId=return-task-1");
        assertThat(notification.traceId()).isEqualTo("trace-workflow");
    }

    @Test
    void transferTodoQueuesHighPriorityExternalCompensationWhenQuietHoursAreEnabled() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo pending = new WorkflowTodo(
            null,
            "todo-followup-1",
            "tenant-A",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "return-task-1",
            "随访异常返院任务",
            "患者随访异常，需要安排回院确认",
            WorkflowPriority.HIGH,
            WorkflowTodoStatus.PENDING,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            "enc-1",
            now.plusSeconds(1800),
            "/clinical/followup?taskId=return-task-1",
            null,
            null,
            null,
            null,
            null,
            "trace-followup",
            now,
            "system",
            now,
            "system");
        when(todos.findByTenantIdAndTodoId("tenant-A", "todo-followup-1")).thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.findByTenantIdAndDedupeKey(
                "tenant-A",
                "todo:todo-followup-1:transferred:nurse-2"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        WorkflowNotificationSettingsResponse settings = externalSettings(false, false, true, true);
        when(notificationSettings.getSettingsForUser("tenant-A", "nurse-2")).thenReturn(settings);
        when(notificationSettings.isMutedByQuietHours(eq(WorkflowNotificationLevel.HIGH), eq(settings), any(LocalTime.class)))
            .thenReturn(false);

        service.transferTodo(
            "todo-followup-1",
            new WorkflowTodoTransferRequest("nurse-2", "NURSING", "交由护理站安排回院确认"));

        ArgumentCaptor<IntegrationOutboundRequestDto> outboundCaptor =
            ArgumentCaptor.forClass(IntegrationOutboundRequestDto.class);
        verify(integrationService).enqueueOutboundMessage(eq("tenant-A"), outboundCaptor.capture());
        IntegrationOutboundRequestDto request = outboundCaptor.getValue();
        assertThat(request.adapterId()).isEqualTo("notification-push");
        assertThat(request.protocolType()).isEqualTo("PUSH");
        assertThat(request.payload().path("channel").asText()).isEqualTo("push");
        assertThat(request.payload().path("recipientId").asText()).isEqualTo("nurse-2");
        assertThat(request.payload().path("level").asText()).isEqualTo("HIGH");
    }

    @Test
    void listTodosProjectsPendingRecommendationCardsIntoUnifiedTodoCenter() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        when(followupTasks.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of());
        when(affectedTasks.pageByTenantId("tenant-A", 0, 200)).thenReturn(List.of());
        when(recommendationCards.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of(
            new RecommendationWorkflowTodoRow(
                "card-high-risk-1",
                RecommendationCardType.MEDICATION,
                "抗凝用药风险提醒",
                "患者当前医嘱满足抗凝风险规则",
                RecommendationRiskLevel.HIGH,
                RecommendationCardStatus.PENDING,
                now.plusSeconds(3600),
                "trace-recommendation",
                now,
                "patient-1",
                "enc-1",
                "order-sign",
                "WARD_ORDER")));
        when(todos.findByTenantIdAndSourceTypeAndSourceId(
                "tenant-A",
                WorkflowTodoSourceType.RECOMMENDATION_CARD,
                "card-high-risk-1"))
            .thenReturn(Optional.empty());
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(todos.countByFilter("tenant-A", null, null, null, null, null)).thenReturn(1L);
        when(todos.pageByFilter("tenant-A", null, null, null, null, null, 0, 20)).thenReturn(List.of(
            new WorkflowTodo(
                null,
                "todo-card-1",
                "tenant-A",
                WorkflowTodoSourceType.RECOMMENDATION_CARD,
                "card-high-risk-1",
                "抗凝用药风险提醒",
                "患者当前医嘱满足抗凝风险规则",
                WorkflowPriority.HIGH,
                WorkflowTodoStatus.PENDING,
                null,
                "DOCTOR",
                "patient-1",
                "enc-1",
                now.plusSeconds(3600),
                "/recommendations?cardId=card-high-risk-1",
                null,
                null,
                null,
                null,
                null,
                "trace-recommendation",
                now,
                "system",
                now,
                "system")));

        PageResponse<WorkflowTodoResponse> page = service.listTodos(
            new WorkflowTodoFilter(null, null, null, null, null),
            new PageRequest(1, 20, null));

        assertThat(page.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.sourceType()).isEqualTo(WorkflowTodoSourceType.RECOMMENDATION_CARD);
                assertThat(item.deepLink()).contains("card-high-risk-1");
            });
        verify(todos).save(any(WorkflowTodo.class));
    }

    @Test
    void listTodosProjectsClinicalRecommendationTypesIntoCollaborationSources() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        when(followupTasks.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of());
        when(affectedTasks.pageByTenantId("tenant-A", 0, 200)).thenReturn(List.of());
        when(recommendationCards.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of(
            new RecommendationWorkflowTodoRow(
                "card-nursing-1",
                RecommendationCardType.NURSING,
                "压疮风险护理评估",
                "患者护理评估提示压疮风险升高",
                RecommendationRiskLevel.HIGH,
                RecommendationCardStatus.PENDING,
                now.plusSeconds(1800),
                "trace-nursing",
                now,
                "patient-1",
                "enc-1",
                "nursing-assessment",
                "WARD_NURSING"),
            new RecommendationWorkflowTodoRow(
                "card-report-1",
                RecommendationCardType.EXAM,
                "检验报告解读",
                "报告结果触发复核建议",
                RecommendationRiskLevel.MEDIUM,
                RecommendationCardStatus.PENDING,
                now.plusSeconds(3600),
                "trace-report",
                now,
                "patient-1",
                "enc-1",
                "REPORT_SUBMIT",
                "REPORT_REVIEW"),
            new RecommendationWorkflowTodoRow(
                "card-knowledge-1",
                RecommendationCardType.KNOWLEDGE,
                "床旁知识卡",
                "当前诊疗上下文命中知识卡",
                RecommendationRiskLevel.LOW,
                RecommendationCardStatus.VIEWED,
                now.plusSeconds(7200),
                "trace-knowledge",
                now,
                "patient-1",
                "enc-1",
                "patient-view",
                "BEDSIDE_KNOWLEDGE")));
        when(todos.findByTenantIdAndSourceTypeAndSourceId(eq("tenant-A"), any(), any()))
            .thenReturn(Optional.empty());
        when(todos.findRecommendationDerivedByTenantIdAndSourceId(eq("tenant-A"), any()))
            .thenReturn(Optional.empty());
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(todos.countByFilter("tenant-A", null, null, null, null, null)).thenReturn(3L);
        when(todos.pageByFilter("tenant-A", null, null, null, null, null, 0, 20)).thenReturn(List.of(
            workflowTodo("todo-nursing-1", WorkflowTodoSourceType.NURSING_TASK,
                "card-nursing-1", "压疮风险护理评估", "patient-1", "enc-1", "NURSING", "trace-nursing", now),
            workflowTodo("todo-report-1", WorkflowTodoSourceType.REPORT_INTERPRETATION,
                "card-report-1", "检验报告解读", "patient-1", "enc-1", "DOCTOR", "trace-report", now),
            workflowTodo("todo-knowledge-1", WorkflowTodoSourceType.BEDSIDE_KNOWLEDGE,
                "card-knowledge-1", "床旁知识卡", "patient-1", "enc-1", "DOCTOR", "trace-knowledge", now)));

        PageResponse<WorkflowTodoResponse> page = service.listTodos(
            new WorkflowTodoFilter(null, null, null, null, null),
            new PageRequest(1, 20, null));

        assertThat(page.items()).extracting(WorkflowTodoResponse::sourceType)
            .containsExactly(
                WorkflowTodoSourceType.NURSING_TASK,
                WorkflowTodoSourceType.REPORT_INTERPRETATION,
                WorkflowTodoSourceType.BEDSIDE_KNOWLEDGE);
        ArgumentCaptor<WorkflowTodo> todoCaptor = ArgumentCaptor.forClass(WorkflowTodo.class);
        verify(todos, times(3)).save(todoCaptor.capture());
        assertThat(todoCaptor.getAllValues()).extracting(WorkflowTodo::sourceType)
            .containsExactly(
                WorkflowTodoSourceType.NURSING_TASK,
                WorkflowTodoSourceType.REPORT_INTERPRETATION,
                WorkflowTodoSourceType.BEDSIDE_KNOWLEDGE);
        assertThat(todoCaptor.getAllValues()).extracting(WorkflowTodo::assigneeRole)
            .containsExactly("NURSING", "DOCTOR", "DOCTOR");
    }

    @Test
    void listNotificationsDeduplicatesRealFollowupNotificationEventsAndReadBackIsPersisted() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        when(followupEvents.pageNotificationRows("tenant-A", 0, 200)).thenReturn(List.of(
            new FollowupNotificationRow(
                "fe-notify-1",
                "fp-1",
                "patient-1",
                "enc-1",
                "ft-return-1",
                "followup-doctor",
                "DOCTOR",
                "随访异常回院通知",
                "患者随访异常，需要安排回院确认",
                "trace-followup",
                now)));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "followup:fe-notify-1"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.countByFilter("tenant-A", null, null, null)).thenReturn(1L);
        WorkflowNotification unread = new WorkflowNotification(
            null,
            "notify-followup-1",
            "tenant-A",
            WorkflowNotificationSourceType.FOLLOWUP_EVENT,
            "fe-notify-1",
            "followup:fe-notify-1",
            "随访异常回院通知",
            "患者随访异常，需要安排回院确认",
            WorkflowNotificationLevel.HIGH,
            WorkflowNotificationStatus.UNREAD,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            "enc-1",
            "/clinical/followup?taskId=ft-return-1",
            null,
            null,
            "trace-followup",
            now,
            "system",
            now,
            "system");
        when(notifications.pageByFilter("tenant-A", null, null, null, 0, 20)).thenReturn(List.of(unread));
        when(notifications.findByTenantIdAndNotificationId("tenant-A", "notify-followup-1"))
            .thenReturn(Optional.of(unread));

        PageResponse<WorkflowNotificationResponse> page = service.listNotifications(
            new WorkflowNotificationFilter(null, null, null),
            new PageRequest(1, 20, null));
        WorkflowNotificationResponse read = service.markNotificationRead("notify-followup-1");

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.sourceType()).isEqualTo(WorkflowNotificationSourceType.FOLLOWUP_EVENT);
                assertThat(item.status()).isEqualTo(WorkflowNotificationStatus.UNREAD);
                assertThat(item.patientId()).isEqualTo("patient-1");
            });
        assertThat(read.status()).isEqualTo(WorkflowNotificationStatus.READ);
        assertThat(read.readBy()).isEqualTo("doctor-1");
        ArgumentCaptor<WorkflowNotification> notificationCaptor =
            ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications, times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues().get(0).recipientId()).isEqualTo("followup-doctor");
        assertThat(notificationCaptor.getAllValues().get(0).recipientRole()).isEqualTo("DOCTOR");
    }

    @Test
    void listNotificationsProjectsProcessedClinicalEventsAsSyncEventNotifications() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        when(followupEvents.pageNotificationRows("tenant-A", 0, 200)).thenReturn(List.of());
        when(clinicalEvents.pageByFilter("tenant-A", null, null, ClinicalEventStatus.PROCESSED.name(), null, 0, 200))
            .thenReturn(List.of(new ClinicalEvent(
                1L,
                "evt-report-1",
                "tenant-A",
                ClinicalEventType.REPORT,
                ClinicalEventTriggerPoint.RESULT_REVIEW,
                "idem-report-1",
                null,
                "{\"tenantId\":\"tenant-A\",\"departmentId\":\"dept-A\"}",
                "patient-1",
                "enc-1",
                "LIS",
                "pkg-1",
                "digest-report-1",
                now.minusSeconds(30),
                now.minusSeconds(20),
                "snap-report-1",
                ClinicalEventStatus.PROCESSED,
                null,
                null,
                0,
                null,
                "trace-report")));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "clinical-event:evt-report-1"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.countByFilter("tenant-A", null, null, null)).thenReturn(1L);
        when(notifications.pageByFilter("tenant-A", null, null, null, 0, 20)).thenReturn(List.of(
            new WorkflowNotification(
                null,
                "notify-event-1",
                "tenant-A",
                WorkflowNotificationSourceType.SYNC_EVENT,
                "evt-report-1",
                "clinical-event:evt-report-1",
                "临床同步事件已处理",
                "LIS 的报告查看事件已进入临床事件引擎并完成处理",
                WorkflowNotificationLevel.INFO,
                WorkflowNotificationStatus.UNREAD,
                null,
                null,
                "patient-1",
                "enc-1",
                "/rule/validate?eventId=evt-report-1",
                null,
                null,
                "trace-report",
                now,
                "system",
                now,
                "system")));

        PageResponse<WorkflowNotificationResponse> page = service.listNotifications(
            new WorkflowNotificationFilter(null, null, null),
            new PageRequest(1, 20, null));

        assertThat(page.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.sourceType()).isEqualTo(WorkflowNotificationSourceType.SYNC_EVENT);
                assertThat(item.message()).contains("报告查看事件", "已进入临床事件引擎");
                assertThat(item.patientId()).isEqualTo("patient-1");
                assertThat(item.encounterId()).isEqualTo("enc-1");
                assertThat(item.deepLink()).contains("evt-report-1");
            });
        ArgumentCaptor<WorkflowNotification> notificationCaptor =
            ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications).save(notificationCaptor.capture());
        WorkflowNotification notification = notificationCaptor.getValue();
        assertThat(notification.dedupeKey()).isEqualTo("clinical-event:evt-report-1");
        assertThat(notification.level()).isEqualTo(WorkflowNotificationLevel.INFO);
        assertThat(notification.recipientId()).isNull();
        assertThat(notification.sourceType()).isEqualTo(WorkflowNotificationSourceType.SYNC_EVENT);
    }

    private WorkflowTodo workflowTodo(
            String todoId,
            WorkflowTodoSourceType sourceType,
            String sourceId,
            String title,
            String patientId,
            String encounterId,
            String assigneeRole,
            String traceId,
            Instant now) {
        return new WorkflowTodo(
            null,
            todoId,
            "tenant-A",
            sourceType,
            sourceId,
            title,
            "真实推荐卡来源投影",
            WorkflowPriority.MEDIUM,
            WorkflowTodoStatus.PENDING,
            null,
            assigneeRole,
            patientId,
            encounterId,
            now.plusSeconds(3600),
            "/cdss/fatigue?cardId=" + sourceId,
            null,
            null,
            null,
            null,
            null,
            traceId,
            now,
            "system",
            now,
            "system");
    }

    private static WorkflowNotificationSettingsResponse externalSettings(
            boolean smsEnabled,
            boolean emailEnabled,
            boolean pushEnabled,
            boolean quietHoursEnabled) {
        return new WorkflowNotificationSettingsResponse(
            true,
            smsEnabled,
            emailEnabled,
            pushEnabled,
            quietHoursEnabled,
            "22:00",
            "07:00",
            Set.of(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH),
            false,
            1,
            Instant.parse("2026-06-04T08:00:00Z"),
            "doctor-1");
    }

    private static IntegrationOutboundResultDto outboundResult(IntegrationOutboundRequestDto request) {
        return new IntegrationOutboundResultDto(
            request.messageId(),
            request.traceId(),
            request.adapterId(),
            "NOT_CONNECTED",
            false,
            true,
            "未接入真实外部发送连接器，已登记异步补偿，不阻断主流程");
    }
}
