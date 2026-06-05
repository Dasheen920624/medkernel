package com.medkernel.engine.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.medkernel.engine.integration.domain.IntegrationMessageLog;
import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.dto.IntegrationOutboundResultDto;
import com.medkernel.engine.integration.repository.IntegrationMessageLogRepository;
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
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
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
    private IntegrationMessageLogRepository integrationLogs;
    private WorkflowNotificationSettingsService notificationSettings;
    private AuditRecorder auditRecorder;
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
        integrationLogs = mock(IntegrationMessageLogRepository.class);
        notificationSettings = mock(WorkflowNotificationSettingsService.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new WorkflowCollaborationService(
            todos,
            notifications,
            followupTasks,
            followupEvents,
            affectedTasks,
            recommendationCards,
            clinicalEvents,
            integrationService,
            integrationLogs,
            notificationSettings,
            auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-workflow", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(recommendationCards.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of());
        when(clinicalEvents.pageByFilter("tenant-A", null, null, ClinicalEventStatus.PROCESSED.name(), null, 0, 200))
            .thenReturn(List.of());
        when(todos.countByVisibleAssigneeScope(eq("tenant-A"), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(0L);
        when(todos.pageByVisibleAssigneeScope(
                eq("tenant-A"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt()))
            .thenReturn(List.of());
        when(notifications.countByVisibleRecipientScope(eq("tenant-A"), any(), any(), any(), any(), any()))
            .thenReturn(0L);
        when(notifications.pageByVisibleRecipientScope(
                eq("tenant-A"),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt()))
            .thenReturn(List.of());
        when(notifications.findByTenantIdAndDedupeKey(eq("tenant-A"), any())).thenReturn(Optional.empty());
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
        when(todos.countByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null)).thenReturn(2L);
        when(todos.pageByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null, 0, 20)).thenReturn(List.of(
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
    void listTodosCreatesMissingPendingTodoNotificationForExistingFollowupTodoAndQueuesCompensation() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        FollowupWorkflowTodoRow source = new FollowupWorkflowTodoRow(
            "ft-return-1",
            "fp-1",
            FollowupTaskType.RETURN_VISIT,
            FollowupTaskStatus.ABNORMAL_RETURN,
            "patient-1",
            "enc-1",
            now.plusSeconds(1800),
            "followup-doctor",
            "DOCTOR",
            "trace-followup",
            now);
        WorkflowTodo existingTodo = new WorkflowTodo(
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
            now.plusSeconds(1800),
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
            "system");
        when(followupTasks.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of(source));
        when(affectedTasks.pageByTenantId("tenant-A", 0, 200)).thenReturn(List.of());
        when(todos.findByTenantIdAndSourceTypeAndSourceId(
                "tenant-A",
                WorkflowTodoSourceType.FOLLOWUP_TASK,
                "ft-return-1"))
            .thenReturn(Optional.of(existingTodo));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "todo:todo-followup-1:created"))
            .thenReturn(Optional.empty());
        WorkflowNotificationSettingsResponse settings = externalSettings(false, false, true, false, false, true);
        when(notificationSettings.getSettingsForUser("tenant-A", "followup-doctor")).thenReturn(settings);
        when(notificationSettings.isMutedByQuietHours(eq(WorkflowNotificationLevel.HIGH), eq(settings), any(LocalTime.class)))
            .thenReturn(false);
        when(todos.countByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null)).thenReturn(1L);
        when(todos.pageByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null, 0, 20))
            .thenReturn(List.of(existingTodo));

        PageResponse<WorkflowTodoResponse> page = service.listTodos(
            new WorkflowTodoFilter(null, null, null, null, null),
            new PageRequest(1, 20, null));

        assertThat(page.items()).singleElement()
            .satisfies(item -> assertThat(item.todoId()).isEqualTo("todo-followup-1"));
        ArgumentCaptor<WorkflowNotification> notificationCaptor =
            ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications).save(notificationCaptor.capture());
        WorkflowNotification notification = notificationCaptor.getValue();
        assertThat(notification.sourceType()).isEqualTo(WorkflowNotificationSourceType.WORKFLOW_TODO);
        assertThat(notification.sourceId()).isEqualTo("todo-followup-1");
        assertThat(notification.dedupeKey()).isEqualTo("todo:todo-followup-1:created");
        assertThat(notification.title()).isEqualTo("待办待处理");
        assertThat(notification.message()).contains("随访异常返院任务", "待处理");
        assertThat(notification.level()).isEqualTo(WorkflowNotificationLevel.HIGH);
        assertThat(notification.status()).isEqualTo(WorkflowNotificationStatus.UNREAD);
        assertThat(notification.recipientId()).isEqualTo("followup-doctor");
        assertThat(notification.recipientRole()).isEqualTo("DOCTOR");
        assertThat(notification.deepLink()).isEqualTo("/clinical/followup?taskId=ft-return-1");

        ArgumentCaptor<IntegrationOutboundRequestDto> outboundCaptor =
            ArgumentCaptor.forClass(IntegrationOutboundRequestDto.class);
        verify(integrationService).enqueueOutboundMessage(eq("tenant-A"), outboundCaptor.capture());
        assertThat(outboundCaptor.getValue().adapterId()).isEqualTo("notification-push");
        assertThat(outboundCaptor.getValue().payload().path("sourceId").asText()).isEqualTo("todo-followup-1");
        assertThat(outboundCaptor.getValue().payload().path("level").asText()).isEqualTo("HIGH");
        assertThat(outboundCaptor.getValue().payload().path("recipientId").asText()).isEqualTo("followup-doctor");
        assertThat(outboundCaptor.getValue().payload().has("patientId")).isFalse();
        assertThat(outboundCaptor.getValue().payload().has("message")).isFalse();
    }

    @Test
    void listTodosDoesNotRepeatPendingTodoNotificationWhenDedupeKeyExists() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo existingTodo = new WorkflowTodo(
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
            now.plusSeconds(1800),
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
            "system");
        when(followupTasks.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of(
            new FollowupWorkflowTodoRow(
                "ft-return-1",
                "fp-1",
                FollowupTaskType.RETURN_VISIT,
                FollowupTaskStatus.ABNORMAL_RETURN,
                "patient-1",
                "enc-1",
                now.plusSeconds(1800),
                "followup-doctor",
                "DOCTOR",
                "trace-followup",
                now)));
        when(affectedTasks.pageByTenantId("tenant-A", 0, 200)).thenReturn(List.of());
        when(todos.findByTenantIdAndSourceTypeAndSourceId(
                "tenant-A",
                WorkflowTodoSourceType.FOLLOWUP_TASK,
                "ft-return-1"))
            .thenReturn(Optional.of(existingTodo));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "todo:todo-followup-1:created"))
            .thenReturn(Optional.of(new WorkflowNotification(
                null,
                "notify-todo-created-1",
                "tenant-A",
                WorkflowNotificationSourceType.WORKFLOW_TODO,
                "todo-followup-1",
                "todo:todo-followup-1:created",
                "待办待处理",
                "待办「随访异常返院任务」待处理",
                WorkflowNotificationLevel.HIGH,
                WorkflowNotificationStatus.UNREAD,
                "followup-doctor",
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
                "system")));
        when(todos.countByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null)).thenReturn(1L);
        when(todos.pageByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null, 0, 20))
            .thenReturn(List.of(existingTodo));

        service.listTodos(
            new WorkflowTodoFilter(null, null, null, null, null),
            new PageRequest(1, 20, null));

        verify(notifications, never()).save(any(WorkflowNotification.class));
        verify(integrationService, never()).enqueueOutboundMessage(
            eq("tenant-A"),
            any(IntegrationOutboundRequestDto.class));
    }

    @Test
    void listTodosDefaultsToCurrentUserAndOrganizationScopeWhenAssigneeFilterMissing() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo ownTodo = new WorkflowTodo(
            null,
            "todo-own-1",
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
        WorkflowTodo organizationTodo = new WorkflowTodo(
            null,
            "todo-org-1",
            "tenant-A",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-org-1",
            "临床提醒复核",
            "未指定个人责任人的组织范围待办",
            WorkflowPriority.HIGH,
            WorkflowTodoStatus.PENDING,
            null,
            "DOCTOR",
            "patient-2",
            "enc-2",
            now.plusSeconds(900),
            "/cdss/fatigue?cardId=card-org-1",
            null,
            null,
            null,
            null,
            null,
            "trace-card",
            now,
            "system",
            now,
            "system");
        when(followupTasks.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of());
        when(affectedTasks.pageByTenantId("tenant-A", 0, 200)).thenReturn(List.of());
        when(todos.countByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null))
            .thenReturn(2L);
        when(todos.pageByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null, 0, 20))
            .thenReturn(List.of(organizationTodo, ownTodo));

        PageResponse<WorkflowTodoResponse> page = service.listTodos(
            new WorkflowTodoFilter(null, null, null, null, null),
            new PageRequest(1, 20, null));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(WorkflowTodoResponse::todoId)
            .containsExactly("todo-org-1", "todo-own-1");
        verify(todos).countByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null);
        verify(todos).pageByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null, 0, 20);
    }

    @Test
    void listTodosPassesSelectedOrganizationScopeToRepositoryFilter() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-workflow",
            new OrgScope("tenant-A", null, null, null, null, "dept-a", null),
            "doctor-1"));
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo selectedTodo = new WorkflowTodo(
            null,
            "todo-selected-1",
            "tenant-A",
            "spec-a1",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-selected-1",
            "临床提醒复核",
            "选中组织范围内的真实待办",
            WorkflowPriority.HIGH,
            WorkflowTodoStatus.PENDING,
            null,
            "DOCTOR",
            "patient-1",
            "enc-1",
            now.plusSeconds(900),
            "/cdss/fatigue?cardId=card-selected-1",
            null,
            null,
            null,
            null,
            null,
            "trace-card",
            now,
            "system",
            now,
            "system");
        when(followupTasks.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of());
        when(affectedTasks.pageByTenantId("tenant-A", 0, 200)).thenReturn(List.of());
        when(todos.countByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            null,
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            null,
            "spec-a1"))
            .thenReturn(1L);
        when(todos.pageByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            null,
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            null,
            "spec-a1",
            0,
            20))
            .thenReturn(List.of(selectedTodo));

        PageResponse<WorkflowTodoResponse> page = service.listTodos(
            new WorkflowTodoFilter(null, null, null, null, null, "spec-a1"),
            new PageRequest(1, 20, null));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(WorkflowTodoResponse::orgUnitId)
            .containsExactly("spec-a1");
        verify(todos).countByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            null,
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            null,
            "spec-a1");
        verify(todos).pageByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            null,
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            null,
            "spec-a1",
            0,
            20);
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-safety-1", "doctor-1", null))
            .thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowTodoResponse completed = service.completeTodo(
            "todo-safety-1",
            new WorkflowTodoCompleteRequest("已复核患者病例，未发现仍在执行的旧版医嘱"));

        assertThat(completed.status()).isEqualTo(WorkflowTodoStatus.COMPLETED);
        assertThat(completed.completedBy()).isEqualTo("doctor-1");
        assertThat(completed.completionReason()).contains("已复核患者病例");
        verify(todos).save(any(WorkflowTodo.class));
        ArgumentCaptor<AuditRecordCommand> auditCaptor =
            ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditRecordCommand audit = auditCaptor.getValue();
        assertThat(audit.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(audit.targetType()).isEqualTo("workflow_todo");
        assertThat(audit.targetId()).isEqualTo("todo-safety-1");
        assertThat(audit.summary()).contains("完成待办", "todo-safety-1");
        assertThat(audit.before()).isInstanceOf(Map.class);
        assertThat(audit.after()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) audit.before();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) audit.after();
        assertThat(before)
            .containsEntry("status", "PENDING")
            .containsEntry("assigneeId", "doctor-1");
        assertThat(after)
            .containsEntry("status", "COMPLETED")
            .containsEntry("completedBy", "doctor-1")
            .containsEntry("traceId", "trace-workflow")
            .doesNotContainKeys("patientId", "message", "summary");
    }

    @Test
    void completeTodoRejectsTodoAssignedToAnotherUserWithoutPersisting() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo otherUserTodo = new WorkflowTodo(
            null,
            "todo-other-1",
            "tenant-A",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "return-task-other",
            "随访任务待处理",
            "其他医生的个人待办",
            WorkflowPriority.MEDIUM,
            WorkflowTodoStatus.PENDING,
            "doctor-2",
            "DOCTOR",
            "patient-2",
            "enc-2",
            now.plusSeconds(1800),
            "/clinical/followup?taskId=return-task-other",
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-other-1", "doctor-1", null))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeTodo(
                "todo-other-1",
                new WorkflowTodoCompleteRequest("误处理他人待办")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("协同待办");
        verify(todos, never()).save(any(WorkflowTodo.class));
        verify(notifications, never()).save(any(WorkflowNotification.class));
        verify(auditRecorder, never()).record(any(AuditRecordCommand.class));
    }

    @Test
    void completeTodoRejectsSiblingOrganizationTodoWithoutPersisting() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-workflow",
            new OrgScope("tenant-A", null, null, null, null, "dept-a", null),
            "doctor-1"));
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-sibling-1", "doctor-1", "dept-a"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeTodo(
                "todo-sibling-1",
                new WorkflowTodoCompleteRequest("误处理同租户其他科室待办")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("协同待办");

        verify(todos, never()).save(any(WorkflowTodo.class));
        verify(notifications, never()).save(any(WorkflowNotification.class));
        verify(auditRecorder, never()).record(any(AuditRecordCommand.class));
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-safety-1", "doctor-1", null))
            .thenReturn(Optional.of(pending));
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-safety-1", "doctor-1", null))
            .thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "todo:todo-safety-1:completed"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        WorkflowNotificationSettingsResponse settings = externalSettings(true, true, true, true, true, false);
        when(notificationSettings.getSettingsForUser("tenant-A", "doctor-1")).thenReturn(settings);
        when(notificationSettings.isMutedByQuietHours(eq(WorkflowNotificationLevel.INFO), eq(settings), any(LocalTime.class)))
            .thenReturn(false);

        service.completeTodo(
            "todo-safety-1",
            new WorkflowTodoCompleteRequest("已复核患者病例，未发现仍在执行的旧版医嘱"));

        ArgumentCaptor<IntegrationOutboundRequestDto> outboundCaptor =
            ArgumentCaptor.forClass(IntegrationOutboundRequestDto.class);
        verify(integrationService, times(5)).enqueueOutboundMessage(eq("tenant-A"), outboundCaptor.capture());
        assertThat(outboundCaptor.getAllValues()).extracting(IntegrationOutboundRequestDto::adapterId)
            .containsExactly(
                "notification-sms",
                "notification-email",
                "notification-push",
                "notification-webhook",
                "notification-in-hospital");
        assertThat(outboundCaptor.getAllValues()).extracting(IntegrationOutboundRequestDto::protocolType)
            .containsExactly("SMS", "EMAIL", "PUSH", "WEBHOOK", "IN_HOSPITAL_MESSAGE");
        assertThat(outboundCaptor.getAllValues()).extracting(request -> request.payload().path("channel").asText())
            .containsExactly("sms", "email", "push", "webhook", "in-hospital");
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-low-1", "doctor-1", null))
            .thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.findByTenantIdAndDedupeKey("tenant-A", "todo:todo-low-1:completed"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        WorkflowNotificationSettingsResponse settings = externalSettings(true, true, false, false, false, true);
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-followup-1", "doctor-1", null))
            .thenReturn(Optional.of(pending));
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
        ArgumentCaptor<AuditRecordCommand> auditCaptor =
            ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditRecordCommand audit = auditCaptor.getValue();
        assertThat(audit.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(audit.targetType()).isEqualTo("workflow_todo");
        assertThat(audit.targetId()).isEqualTo("todo-followup-1");
        assertThat(audit.summary()).contains("转交待办", "todo-followup-1", "nurse-2");
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) audit.before();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) audit.after();
        assertThat(before)
            .containsEntry("status", "PENDING")
            .containsEntry("assigneeId", "doctor-1");
        assertThat(after)
            .containsEntry("status", "TRANSFERRED")
            .containsEntry("assigneeId", "nurse-2")
            .containsEntry("transferredTo", "nurse-2")
            .containsEntry("transferReason", "交由护理站安排回院确认")
            .containsEntry("traceId", "trace-workflow")
            .doesNotContainKeys("patientId", "message", "summary");
    }

    @Test
    void transferTodoRejectsEmptyRequestBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.transferTodo("todo-followup-1", null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("接收人不能为空");
    }

    @Test
    void transferTodoRejectsTodoAssignedToAnotherUserWithoutPersisting() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowTodo otherUserTodo = new WorkflowTodo(
            null,
            "todo-other-1",
            "tenant-A",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "return-task-other",
            "随访任务待处理",
            "其他医生的个人待办",
            WorkflowPriority.MEDIUM,
            WorkflowTodoStatus.PENDING,
            "doctor-2",
            "DOCTOR",
            "patient-2",
            "enc-2",
            now.plusSeconds(1800),
            "/clinical/followup?taskId=return-task-other",
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-other-1", "doctor-1", null))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferTodo(
                "todo-other-1",
                new WorkflowTodoTransferRequest("nurse-2", "NURSING", "误转交他人待办")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("协同待办");
        verify(todos, never()).save(any(WorkflowTodo.class));
        verify(notifications, never()).save(any(WorkflowNotification.class));
        verify(auditRecorder, never()).record(any(AuditRecordCommand.class));
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-followup-1", "doctor-1", null))
            .thenReturn(Optional.of(pending));
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
        when(todos.findVisibleByTenantIdAndTodoId("tenant-A", "todo-followup-1", "doctor-1", null))
            .thenReturn(Optional.of(pending));
        when(todos.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notifications.findByTenantIdAndDedupeKey(
                "tenant-A",
                "todo:todo-followup-1:transferred:nurse-2"))
            .thenReturn(Optional.empty());
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        WorkflowNotificationSettingsResponse settings = externalSettings(false, false, true, false, false, true);
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
        when(todos.countByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null)).thenReturn(1L);
        when(todos.pageByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null, 0, 20)).thenReturn(List.of(
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
        when(todos.countByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null)).thenReturn(3L);
        when(todos.pageByVisibleAssigneeScope("tenant-A", null, null, null, null, "doctor-1", null, null, 0, 20)).thenReturn(List.of(
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
        when(notifications.countByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null)).thenReturn(1L);
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
        when(notifications.pageByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null, 0, 20)).thenReturn(List.of(unread));
        when(notifications.findVisibleByTenantIdAndNotificationId("tenant-A", "notify-followup-1", "doctor-1", null))
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
        ArgumentCaptor<AuditRecordCommand> auditCaptor =
            ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(auditCaptor.capture());
        AuditRecordCommand audit = auditCaptor.getValue();
        assertThat(audit.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(audit.targetType()).isEqualTo("workflow_notification");
        assertThat(audit.targetId()).isEqualTo("notify-followup-1");
        assertThat(audit.summary()).contains("标记通知已读", "notify-followup-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> before = (Map<String, Object>) audit.before();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) audit.after();
        assertThat(before)
            .containsEntry("status", "UNREAD")
            .containsEntry("recipientId", "doctor-1");
        assertThat(after)
            .containsEntry("status", "READ")
            .containsEntry("readBy", "doctor-1")
            .containsEntry("traceId", "trace-workflow")
            .doesNotContainKeys("patientId", "message", "summary");
        ArgumentCaptor<WorkflowNotification> notificationCaptor =
            ArgumentCaptor.forClass(WorkflowNotification.class);
        verify(notifications, times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues().get(0).recipientId()).isEqualTo("followup-doctor");
        assertThat(notificationCaptor.getAllValues().get(0).recipientRole()).isEqualTo("DOCTOR");
    }

    @Test
    void listNotificationsReturnsHonestExternalDeliveryCompensationStatus() {
        Instant now = Instant.parse("2026-06-05T08:00:00Z");
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
        when(notifications.countByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null)).thenReturn(1L);
        when(notifications.pageByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null, 0, 20))
            .thenReturn(List.of(unread));
        when(integrationLogs.findByMessageIdAndTenantId("notify-out-sms-notify-followup-1", "tenant-A"))
            .thenReturn(Optional.of(new IntegrationMessageLog(
                null,
                "notify-out-sms-notify-followup-1",
                "tenant-A",
                "trace-followup",
                "OUTBOUND",
                "短信通知通道",
                "SMS",
                "通知外发补偿（短信通知通道）：随访异常回院通知",
                "{\"degradeReason\":\"未接入真实外部发送连接器\"}",
                "NOT_CONNECTED",
                0,
                3,
                "未接入真实外部发送连接器，已登记异步补偿，不阻断主流程",
                now,
                "system",
                now,
                "system")));

        PageResponse<WorkflowNotificationResponse> page = service.listNotifications(
            new WorkflowNotificationFilter(null, null, null),
            new PageRequest(1, 20, null));

        assertThat(page.items()).singleElement()
            .satisfies(item -> assertThat(item.externalDeliveries()).singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.channelCode()).isEqualTo("sms");
                    assertThat(delivery.channelName()).isEqualTo("短信通知通道");
                    assertThat(delivery.status()).isEqualTo("NOT_CONNECTED");
                    assertThat(delivery.compensationRequired()).isTrue();
                    assertThat(delivery.errorMessage()).contains("未接入真实外部发送连接器");
                }));
    }

    @Test
    void listNotificationsDefaultsToCurrentUserAndOrganizationScopeWhenRecipientFilterMissing() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowNotification ownNotification = new WorkflowNotification(
            null,
            "notify-own-1",
            "tenant-A",
            WorkflowNotificationSourceType.WORKFLOW_TODO,
            "todo-own-1",
            "todo:todo-own-1:created",
            "待办待处理",
            "本人待办需要处理",
            WorkflowNotificationLevel.HIGH,
            WorkflowNotificationStatus.UNREAD,
            "doctor-1",
            "DOCTOR",
            "patient-1",
            "enc-1",
            "/workflow/todos",
            null,
            null,
            "trace-own",
            now,
            "system",
            now,
            "system");
        WorkflowNotification organizationNotification = new WorkflowNotification(
            null,
            "notify-org-1",
            "tenant-A",
            WorkflowNotificationSourceType.SYNC_EVENT,
            "event-org-1",
            "clinical-event:event-org-1",
            "临床同步事件已处理",
            "组织范围同步事件通知",
            WorkflowNotificationLevel.INFO,
            WorkflowNotificationStatus.UNREAD,
            null,
            null,
            "patient-2",
            "enc-2",
            "/rule/validate?eventId=event-org-1",
            null,
            null,
            "trace-org",
            now,
            "system",
            now,
            "system");
        when(notifications.countByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null))
            .thenReturn(2L);
        when(notifications.pageByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null, 0, 20))
            .thenReturn(List.of(organizationNotification, ownNotification));

        PageResponse<WorkflowNotificationResponse> page = service.listNotifications(
            new WorkflowNotificationFilter(null, null, null),
            new PageRequest(1, 20, null));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(WorkflowNotificationResponse::notificationId)
            .containsExactly("notify-org-1", "notify-own-1");
        verify(notifications).countByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null);
        verify(notifications).pageByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null, 0, 20);
    }

    @Test
    void listNotificationsPassesSelectedOrganizationScopeToRepositoryFilter() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-workflow",
            new OrgScope("tenant-A", null, null, null, null, "dept-a", null),
            "doctor-1"));
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowNotification selectedNotification = new WorkflowNotification(
            null,
            "notify-selected-1",
            "tenant-A",
            "spec-a1",
            WorkflowNotificationSourceType.SYNC_EVENT,
            "event-selected-1",
            "clinical-event:event-selected-1",
            "临床同步事件已处理",
            "选中组织范围内的真实通知",
            WorkflowNotificationLevel.INFO,
            WorkflowNotificationStatus.UNREAD,
            null,
            null,
            "patient-1",
            "enc-1",
            "/rule/validate?eventId=event-selected-1",
            null,
            null,
            "trace-org",
            now,
            "system",
            now,
            "system");
        when(notifications.countByVisibleRecipientScopeAndOrgUnitFilter(
            "tenant-A",
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            "spec-a1"))
            .thenReturn(1L);
        when(notifications.pageByVisibleRecipientScopeAndOrgUnitFilter(
            "tenant-A",
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            "spec-a1",
            0,
            20))
            .thenReturn(List.of(selectedNotification));

        PageResponse<WorkflowNotificationResponse> page = service.listNotifications(
            new WorkflowNotificationFilter(null, null, null, "spec-a1"),
            new PageRequest(1, 20, null));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(WorkflowNotificationResponse::orgUnitId)
            .containsExactly("spec-a1");
        verify(notifications).countByVisibleRecipientScopeAndOrgUnitFilter(
            "tenant-A",
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            "spec-a1");
        verify(notifications).pageByVisibleRecipientScopeAndOrgUnitFilter(
            "tenant-A",
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            "spec-a1",
            0,
            20);
    }

    @Test
    void markNotificationReadRejectsNotificationForAnotherRecipientWithoutPersisting() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        WorkflowNotification otherRecipientNotification = new WorkflowNotification(
            null,
            "notify-other-1",
            "tenant-A",
            WorkflowNotificationSourceType.WORKFLOW_TODO,
            "todo-other-1",
            "todo:todo-other-1:created",
            "待办待处理",
            "其他医生的个人通知",
            WorkflowNotificationLevel.HIGH,
            WorkflowNotificationStatus.UNREAD,
            "doctor-2",
            "DOCTOR",
            "patient-2",
            "enc-2",
            "/workflow/todos",
            null,
            null,
            "trace-other",
            now,
            "system",
            now,
            "system");
        when(notifications.findVisibleByTenantIdAndNotificationId("tenant-A", "notify-other-1", "doctor-1", null))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markNotificationRead("notify-other-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("通知");
        verify(notifications, never()).save(any(WorkflowNotification.class));
        verify(auditRecorder, never()).record(any(AuditRecordCommand.class));
    }

    @Test
    void markNotificationReadRejectsSiblingOrganizationNotificationWithoutPersisting() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-workflow",
            new OrgScope("tenant-A", null, null, null, null, "dept-a", null),
            "doctor-1"));
        when(notifications.findVisibleByTenantIdAndNotificationId(
                "tenant-A",
                "notify-sibling-1",
                "doctor-1",
                "dept-a"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markNotificationRead("notify-sibling-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("通知");

        verify(notifications, never()).save(any(WorkflowNotification.class));
        verify(auditRecorder, never()).record(any(AuditRecordCommand.class));
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
        when(notifications.countByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null)).thenReturn(1L);
        when(notifications.pageByVisibleRecipientScope("tenant-A", null, null, null, "doctor-1", null, 0, 20)).thenReturn(List.of(
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
            boolean webhookEnabled,
            boolean inHospitalMessageEnabled,
            boolean quietHoursEnabled) {
        return new WorkflowNotificationSettingsResponse(
            true,
            smsEnabled,
            emailEnabled,
            pushEnabled,
            webhookEnabled,
            inHospitalMessageEnabled,
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
