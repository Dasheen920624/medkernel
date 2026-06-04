package com.medkernel.engine.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临床协同待办与通知统一服务。
 *
 * <p>服务只投影已经持久化的真实业务来源，不在浏览器或接口层合成样例数据。
 */
@Service
public class WorkflowCollaborationService {

    private static final int SYNC_BATCH_SIZE = 200;
    private static final String SYSTEM_ACTOR = "system";

    private final WorkflowTodoRepository todos;
    private final WorkflowNotificationRepository notifications;
    private final FollowupTaskRepository followupTasks;
    private final FollowupEventRepository followupEvents;
    private final AffectedCaseTaskRepository affectedTasks;
    private final RecommendationCardRepository recommendationCards;

    public WorkflowCollaborationService(
            WorkflowTodoRepository todos,
            WorkflowNotificationRepository notifications,
            FollowupTaskRepository followupTasks,
            FollowupEventRepository followupEvents,
            AffectedCaseTaskRepository affectedTasks,
            RecommendationCardRepository recommendationCards) {
        this.todos = todos;
        this.notifications = notifications;
        this.followupTasks = followupTasks;
        this.followupEvents = followupEvents;
        this.affectedTasks = affectedTasks;
        this.recommendationCards = recommendationCards;
    }

    /**
     * 查询统一待办，并在查询前同步当前支持的真实来源。
     */
    @Transactional
    public PageResponse<WorkflowTodoResponse> listTodos(WorkflowTodoFilter filter, PageRequest pageRequest) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        syncFollowupTodos(tenantId);
        syncSafetyTodos(tenantId);
        syncRecommendationTodos(tenantId);

        WorkflowTodoFilter safeFilter = filter == null
            ? new WorkflowTodoFilter(null, null, null, null, null)
            : filter;
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        long total = todos.countByFilter(
            tenantId,
            name(safeFilter.status()),
            name(safeFilter.priority()),
            name(safeFilter.sourceType()),
            blankToNull(safeFilter.assigneeId()),
            blankToNull(safeFilter.patientId()));
        List<WorkflowTodoResponse> rows = todos.pageByFilter(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.priority()),
                name(safeFilter.sourceType()),
                blankToNull(safeFilter.assigneeId()),
                blankToNull(safeFilter.patientId()),
                req.offset(),
                req.safeSize()).stream()
            .map(WorkflowTodoResponse::from)
            .toList();
        return PageResponse.of(rows, req, total);
    }

    /**
     * 完成统一待办并持久化审计字段。
     */
    @Transactional
    public WorkflowTodoResponse completeTodo(String todoId, WorkflowTodoCompleteRequest request) {
        RequestContext.Snapshot ctx = requireContext();
        String normalizedReason = requireText(request == null ? null : request.completionReason(), "完成说明");
        String tenantId = ctx.orgScope().tenantId();
        String actor = actor(ctx);
        Instant now = Instant.now();
        WorkflowTodo todo = todos.findByTenantIdAndTodoId(tenantId, todoId)
            .orElseThrow(() -> ApiException.notFound("协同待办"));
        WorkflowTodo completed = new WorkflowTodo(
            todo.id(),
            todo.todoId(),
            todo.tenantId(),
            todo.sourceType(),
            todo.sourceId(),
            todo.title(),
            todo.summary(),
            todo.priority(),
            WorkflowTodoStatus.COMPLETED,
            todo.assigneeId(),
            todo.assigneeRole(),
            todo.patientId(),
            todo.encounterId(),
            todo.dueAt(),
            todo.deepLink(),
            normalizedReason,
            now,
            actor,
            todo.transferredTo(),
            todo.transferReason(),
            ctx.traceId(),
            todo.createdAt(),
            todo.createdBy(),
            now,
            actor);
        return WorkflowTodoResponse.from(todos.save(completed));
    }

    /**
     * 转交统一待办并持久化新责任人与说明。
     */
    @Transactional
    public WorkflowTodoResponse transferTodo(String todoId, WorkflowTodoTransferRequest request) {
        RequestContext.Snapshot ctx = requireContext();
        String transferTo = requireText(request == null ? null : request.transferTo(), "接收人");
        String transferReason = requireText(request == null ? null : request.transferReason(), "转交说明");
        String transferRole = blankToNull(request.transferRole());
        String tenantId = ctx.orgScope().tenantId();
        String actor = actor(ctx);
        Instant now = Instant.now();
        WorkflowTodo todo = todos.findByTenantIdAndTodoId(tenantId, todoId)
            .orElseThrow(() -> ApiException.notFound("协同待办"));
        if (todo.status() != WorkflowTodoStatus.PENDING && todo.status() != WorkflowTodoStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "仅待处理或处理中待办可转交");
        }
        WorkflowTodo transferred = new WorkflowTodo(
            todo.id(),
            todo.todoId(),
            todo.tenantId(),
            todo.sourceType(),
            todo.sourceId(),
            todo.title(),
            todo.summary(),
            todo.priority(),
            WorkflowTodoStatus.TRANSFERRED,
            transferTo,
            transferRole,
            todo.patientId(),
            todo.encounterId(),
            todo.dueAt(),
            todo.deepLink(),
            todo.completionReason(),
            todo.completedAt(),
            todo.completedBy(),
            transferTo,
            transferReason,
            ctx.traceId(),
            todo.createdAt(),
            todo.createdBy(),
            now,
            actor);
        return WorkflowTodoResponse.from(todos.save(transferred));
    }

    /**
     * 查询统一通知，并在查询前同步当前支持的真实通知事件。
     */
    @Transactional
    public PageResponse<WorkflowNotificationResponse> listNotifications(
            WorkflowNotificationFilter filter,
            PageRequest pageRequest) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        syncFollowupNotifications(ctx);

        WorkflowNotificationFilter safeFilter = filter == null
            ? new WorkflowNotificationFilter(null, null, null)
            : filter;
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        long total = notifications.countByFilter(
            tenantId,
            name(safeFilter.status()),
            name(safeFilter.level()),
            blankToNull(safeFilter.recipientId()));
        List<WorkflowNotificationResponse> rows = notifications.pageByFilter(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.level()),
                blankToNull(safeFilter.recipientId()),
                req.offset(),
                req.safeSize()).stream()
            .map(WorkflowNotificationResponse::from)
            .toList();
        return PageResponse.of(rows, req, total);
    }

    /**
     * 标记通知已读并持久化阅读人。
     */
    @Transactional
    public WorkflowNotificationResponse markNotificationRead(String notificationId) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        String actor = actor(ctx);
        Instant now = Instant.now();
        WorkflowNotification notification = notifications.findByTenantIdAndNotificationId(tenantId, notificationId)
            .orElseThrow(() -> ApiException.notFound("通知"));
        WorkflowNotification read = new WorkflowNotification(
            notification.id(),
            notification.notificationId(),
            notification.tenantId(),
            notification.sourceType(),
            notification.sourceId(),
            notification.dedupeKey(),
            notification.title(),
            notification.message(),
            notification.level(),
            WorkflowNotificationStatus.READ,
            notification.recipientId(),
            notification.recipientRole(),
            notification.patientId(),
            notification.encounterId(),
            notification.deepLink(),
            now,
            actor,
            ctx.traceId(),
            notification.createdAt(),
            notification.createdBy(),
            now,
            actor);
        return WorkflowNotificationResponse.from(notifications.save(read));
    }

    private void syncFollowupTodos(String tenantId) {
        List<FollowupWorkflowTodoRow> rows = nullToEmpty(
            followupTasks.pageOpenWorkflowRows(tenantId, 0, SYNC_BATCH_SIZE));
        for (FollowupWorkflowTodoRow row : rows) {
            todos.findByTenantIdAndSourceTypeAndSourceId(
                    tenantId,
                    WorkflowTodoSourceType.FOLLOWUP_TASK,
                    row.taskId())
                .orElseGet(() -> todos.save(fromFollowupTodo(tenantId, row)));
        }
    }

    private void syncSafetyTodos(String tenantId) {
        List<AffectedCaseTask> rows = nullToEmpty(affectedTasks.pageByTenantId(tenantId, 0, SYNC_BATCH_SIZE));
        for (AffectedCaseTask task : rows) {
            if (!isOpenSafetyTask(task)) {
                continue;
            }
            todos.findByTenantIdAndSourceTypeAndSourceId(
                    tenantId,
                    WorkflowTodoSourceType.SAFETY_REVIEW,
                    task.taskKey())
                .orElseGet(() -> todos.save(fromSafetyTask(task)));
        }
    }

    private void syncRecommendationTodos(String tenantId) {
        List<RecommendationWorkflowTodoRow> rows = nullToEmpty(
            recommendationCards.pageOpenWorkflowRows(tenantId, 0, SYNC_BATCH_SIZE));
        for (RecommendationWorkflowTodoRow row : rows) {
            todos.findByTenantIdAndSourceTypeAndSourceId(
                    tenantId,
                    WorkflowTodoSourceType.RECOMMENDATION_CARD,
                    row.cardId())
                .orElseGet(() -> todos.save(fromRecommendationCardTodo(tenantId, row)));
        }
    }

    private void syncFollowupNotifications(RequestContext.Snapshot ctx) {
        String tenantId = ctx.orgScope().tenantId();
        List<FollowupNotificationRow> rows = nullToEmpty(
            followupEvents.pageNotificationRows(tenantId, 0, SYNC_BATCH_SIZE));
        for (FollowupNotificationRow row : rows) {
            String dedupeKey = "followup:" + row.eventId();
            notifications.findByTenantIdAndDedupeKey(tenantId, dedupeKey)
                .orElseGet(() -> notifications.save(fromFollowupNotification(ctx, row, dedupeKey)));
        }
    }

    private WorkflowTodo fromFollowupTodo(String tenantId, FollowupWorkflowTodoRow row) {
        Instant createdAt = row.createdAt() == null ? Instant.now() : row.createdAt();
        return new WorkflowTodo(
            null,
            "todo-" + UUID.randomUUID(),
            tenantId,
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            row.taskId(),
            followupTitle(row),
            followupSummary(row),
            followupPriority(row.status()),
            WorkflowTodoStatus.PENDING,
            blankToNull(row.executorId()),
            blankToNull(row.executorType()),
            row.patientId(),
            row.encounterId(),
            row.dueAt(),
            "/clinical/followup?taskId=" + row.taskId(),
            null,
            null,
            null,
            null,
            null,
            row.traceId(),
            createdAt,
            SYSTEM_ACTOR,
            createdAt,
            SYSTEM_ACTOR);
    }

    private WorkflowTodo fromSafetyTask(AffectedCaseTask task) {
        Instant createdAt = task.createdAt() == null ? Instant.now() : task.createdAt();
        String patientId = task.targetType() == AffectedCaseTargetType.PATIENT_CASE
            || task.targetType() == AffectedCaseTargetType.PATIENT_PATHWAY
            ? task.targetRef()
            : null;
        return new WorkflowTodo(
            null,
            "todo-" + UUID.randomUUID(),
            task.tenantId(),
            WorkflowTodoSourceType.SAFETY_REVIEW,
            task.taskKey(),
            "安全撤回复核任务",
            task.reason(),
            safetyPriority(task.taskType()),
            WorkflowTodoStatus.PENDING,
            blankToNull(task.assignedTo()),
            task.taskType() == AffectedCaseTaskType.PHYSICIAN_REVIEW ? "DOCTOR" : null,
            patientId,
            null,
            task.dueAt(),
            "/provenance?taskKey=" + task.taskKey(),
            null,
            null,
            null,
            null,
            null,
            task.traceId(),
            createdAt,
            task.createdBy(),
            createdAt,
            task.updatedBy());
    }

    private WorkflowTodo fromRecommendationCardTodo(String tenantId, RecommendationWorkflowTodoRow row) {
        Instant createdAt = row.createdAt() == null ? Instant.now() : row.createdAt();
        return new WorkflowTodo(
            null,
            "todo-" + UUID.randomUUID(),
            tenantId,
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            row.cardId(),
            defaultText(row.title(), "临床提醒复核"),
            recommendationSummary(row),
            recommendationPriority(row.riskLevel()),
            WorkflowTodoStatus.PENDING,
            null,
            "DOCTOR",
            row.patientId(),
            row.encounterId(),
            row.expiresAt(),
            "/cdss/fatigue?cardId=" + row.cardId(),
            null,
            null,
            null,
            null,
            null,
            row.traceId(),
            createdAt,
            SYSTEM_ACTOR,
            createdAt,
            SYSTEM_ACTOR);
    }

    private WorkflowNotification fromFollowupNotification(
            RequestContext.Snapshot ctx,
            FollowupNotificationRow row,
            String dedupeKey) {
        Instant createdAt = row.createdAt() == null ? Instant.now() : row.createdAt();
        return new WorkflowNotification(
            null,
            "notify-" + UUID.randomUUID(),
            ctx.orgScope().tenantId(),
            WorkflowNotificationSourceType.FOLLOWUP_EVENT,
            row.eventId(),
            dedupeKey,
            defaultText(row.title(), "随访通知"),
            defaultText(row.message(), "随访事件需要处理"),
            WorkflowNotificationLevel.HIGH,
            WorkflowNotificationStatus.UNREAD,
            blankToNull(row.executorId()),
            blankToNull(row.executorType()),
            row.patientId(),
            row.encounterId(),
            row.taskId() == null ? "/clinical/followup" : "/clinical/followup?taskId=" + row.taskId(),
            null,
            null,
            row.traceId(),
            createdAt,
            SYSTEM_ACTOR,
            createdAt,
            SYSTEM_ACTOR);
    }

    private RequestContext.Snapshot requireContext() {
        RequestContext.Snapshot ctx = RequestContext.snapshot();
        if (!ctx.orgScope().hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return ctx;
    }

    private static boolean isOpenSafetyTask(AffectedCaseTask task) {
        return task.status() == AffectedCaseTaskStatus.OPEN || task.status() == AffectedCaseTaskStatus.IN_PROGRESS;
    }

    private static WorkflowPriority followupPriority(FollowupTaskStatus status) {
        return status == FollowupTaskStatus.ABNORMAL_RETURN ? WorkflowPriority.HIGH : WorkflowPriority.MEDIUM;
    }

    private static WorkflowPriority safetyPriority(AffectedCaseTaskType taskType) {
        return taskType == AffectedCaseTaskType.PHYSICIAN_REVIEW
            ? WorkflowPriority.CRITICAL
            : WorkflowPriority.HIGH;
    }

    private static WorkflowPriority recommendationPriority(RecommendationRiskLevel riskLevel) {
        if (riskLevel == RecommendationRiskLevel.CRITICAL) {
            return WorkflowPriority.CRITICAL;
        }
        if (riskLevel == RecommendationRiskLevel.HIGH) {
            return WorkflowPriority.HIGH;
        }
        if (riskLevel == RecommendationRiskLevel.LOW) {
            return WorkflowPriority.LOW;
        }
        return WorkflowPriority.MEDIUM;
    }

    private static String followupTitle(FollowupWorkflowTodoRow row) {
        if (row.taskType() == FollowupTaskType.RETURN_VISIT) {
            return "随访异常返院任务";
        }
        return "随访任务待处理";
    }

    private static String followupSummary(FollowupWorkflowTodoRow row) {
        if (row.status() == FollowupTaskStatus.ABNORMAL_RETURN) {
            return "患者随访异常，需要安排回院确认";
        }
        return "随访任务需要处理";
    }

    private static String recommendationSummary(RecommendationWorkflowTodoRow row) {
        String summary = defaultText(row.summary(), "临床提醒需要医师复核");
        if (row.status() == RecommendationCardStatus.DEFERRED) {
            return summary + "；此前选择稍后处理";
        }
        if (row.triggerType() == null || row.triggerType().isBlank()) {
            return summary;
        }
        return summary + "；触发点：" + row.triggerType().trim();
    }

    private static String actor(RequestContext.Snapshot ctx) {
        return blankToNull(ctx.userId()) == null ? SYSTEM_ACTOR : ctx.userId().trim();
    }

    private static String requireText(String value, String label) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return normalized;
    }

    private static String defaultText(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
