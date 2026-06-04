package com.medkernel.engine.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    private WorkflowCollaborationService service;

    @BeforeEach
    void setUp() {
        todos = mock(WorkflowTodoRepository.class);
        notifications = mock(WorkflowNotificationRepository.class);
        followupTasks = mock(FollowupTaskRepository.class);
        followupEvents = mock(FollowupEventRepository.class);
        affectedTasks = mock(AffectedCaseTaskRepository.class);
        recommendationCards = mock(RecommendationCardRepository.class);
        service = new WorkflowCollaborationService(
            todos,
            notifications,
            followupTasks,
            followupEvents,
            affectedTasks,
            recommendationCards);
        RequestContext.restore(new RequestContext.Snapshot("trace-workflow", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(recommendationCards.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of());
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
    void listTodosProjectsPendingRecommendationCardsIntoUnifiedTodoCenter() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        when(followupTasks.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of());
        when(affectedTasks.pageByTenantId("tenant-A", 0, 200)).thenReturn(List.of());
        when(recommendationCards.pageOpenWorkflowRows("tenant-A", 0, 200)).thenReturn(List.of(
            new RecommendationWorkflowTodoRow(
                "card-high-risk-1",
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
}
