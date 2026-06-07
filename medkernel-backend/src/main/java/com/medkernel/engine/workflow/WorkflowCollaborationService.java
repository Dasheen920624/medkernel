package com.medkernel.engine.workflow;

import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.ClinicalEvent;
import com.medkernel.engine.context.ClinicalEventRepository;
import com.medkernel.engine.context.ClinicalEventStatus;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.followup.FollowupEventRepository;
import com.medkernel.engine.followup.FollowupTaskRepository;
import com.medkernel.engine.followup.FollowupTaskStatus;
import com.medkernel.engine.followup.FollowupTaskType;
import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.repository.IntegrationMessageLogRepository;
import com.medkernel.engine.integration.service.IntegrationService;
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
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
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
    private static final List<ExternalNotificationChannel> EXTERNAL_NOTIFICATION_CHANNELS = List.of(
        new ExternalNotificationChannel("sms", "notification-sms", "短信通知通道", "SMS"),
        new ExternalNotificationChannel("email", "notification-email", "邮件通知通道", "EMAIL"),
        new ExternalNotificationChannel("push", "notification-push", "移动推送通道", "PUSH"),
        new ExternalNotificationChannel("webhook", "notification-webhook", "Webhook 通知通道", "WEBHOOK"),
        new ExternalNotificationChannel(
            "in-hospital",
            "notification-in-hospital",
            "院内消息通道",
            "IN_HOSPITAL_MESSAGE")
    );

    private final WorkflowTodoRepository todos;
    private final WorkflowNotificationRepository notifications;
    private final FollowupTaskRepository followupTasks;
    private final FollowupEventRepository followupEvents;
    private final AffectedCaseTaskRepository affectedTasks;
    private final RecommendationCardRepository recommendationCards;
    private final ClinicalEventRepository clinicalEvents;
    private final IntegrationService integrationService;
    private final IntegrationMessageLogRepository integrationLogs;
    private final WorkflowNotificationSettingsService notificationSettings;
    private final AuditRecorder auditRecorder;

    public WorkflowCollaborationService(
            WorkflowTodoRepository todos,
            WorkflowNotificationRepository notifications,
            FollowupTaskRepository followupTasks,
            FollowupEventRepository followupEvents,
            AffectedCaseTaskRepository affectedTasks,
            RecommendationCardRepository recommendationCards,
            ClinicalEventRepository clinicalEvents,
            IntegrationService integrationService,
            IntegrationMessageLogRepository integrationLogs,
            WorkflowNotificationSettingsService notificationSettings,
            AuditRecorder auditRecorder) {
        this.todos = todos;
        this.notifications = notifications;
        this.followupTasks = followupTasks;
        this.followupEvents = followupEvents;
        this.affectedTasks = affectedTasks;
        this.recommendationCards = recommendationCards;
        this.clinicalEvents = clinicalEvents;
        this.integrationService = integrationService;
        this.integrationLogs = integrationLogs;
        this.notificationSettings = notificationSettings;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 查询统一待办，并在查询前同步当前支持的真实来源。
     */
    @Transactional
    public PageResponse<WorkflowTodoResponse> listTodos(WorkflowTodoFilter filter, PageRequest pageRequest) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        syncFollowupTodos(ctx);
        syncSafetyTodos(ctx);
        syncRecommendationTodos(ctx);

        WorkflowTodoFilter safeFilter = filter == null
            ? new WorkflowTodoFilter(null, null, null, null, null)
            : filter;
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        String assigneeId = blankToNull(safeFilter.assigneeId());
        String patientId = blankToNull(safeFilter.patientId());
        String selectedOrgUnitId = blankToNull(safeFilter.orgUnitId());
        String currentUserId = currentUserId(ctx);
        String currentOrgUnitId = currentOrgUnitId(ctx);
        long total = selectedOrgUnitId == null
            ? todos.countByVisibleAssigneeScope(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.priority()),
                name(safeFilter.sourceType()),
                assigneeId,
                currentUserId,
                currentOrgUnitId,
                patientId)
            : todos.countByVisibleAssigneeScopeAndOrgUnitFilter(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.priority()),
                name(safeFilter.sourceType()),
                assigneeId,
                currentUserId,
                currentOrgUnitId,
                patientId,
                selectedOrgUnitId);
        List<WorkflowTodo> page = selectedOrgUnitId == null
            ? todos.pageByVisibleAssigneeScope(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.priority()),
                name(safeFilter.sourceType()),
                assigneeId,
                currentUserId,
                currentOrgUnitId,
                patientId,
                req.offset(),
                req.safeSize())
            : todos.pageByVisibleAssigneeScopeAndOrgUnitFilter(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.priority()),
                name(safeFilter.sourceType()),
                assigneeId,
                currentUserId,
                currentOrgUnitId,
                patientId,
                selectedOrgUnitId,
                req.offset(),
                req.safeSize());
        List<WorkflowTodoResponse> rows = page.stream()
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
        WorkflowTodo todo = todos.findVisibleByTenantIdAndTodoId(
                tenantId,
                todoId,
                currentUserId(ctx),
                currentOrgUnitId(ctx))
            .orElseThrow(() -> ApiException.notFound("协同待办"));
        WorkflowTodo completed = new WorkflowTodo(
            todo.id(),
            todo.todoId(),
            todo.tenantId(),
            todo.orgUnitId(),
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
        WorkflowTodo saved = todos.save(completed);
        createCompletionNotificationIfAbsent(ctx, saved, normalizedReason, now, actor);
        recordTodoAudit("完成待办 " + saved.todoId(), todo, saved);
        return WorkflowTodoResponse.from(saved);
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
        WorkflowTodo todo = todos.findVisibleByTenantIdAndTodoId(
                tenantId,
                todoId,
                currentUserId(ctx),
                currentOrgUnitId(ctx))
            .orElseThrow(() -> ApiException.notFound("协同待办"));
        if (todo.status() != WorkflowTodoStatus.PENDING && todo.status() != WorkflowTodoStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "仅待处理或处理中待办可转交");
        }
        WorkflowTodo transferred = new WorkflowTodo(
            todo.id(),
            todo.todoId(),
            todo.tenantId(),
            todo.orgUnitId(),
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
        WorkflowTodo saved = todos.save(transferred);
        createTransferNotificationIfAbsent(ctx, saved, transferReason, now, actor);
        recordTodoAudit("转交待办 " + saved.todoId() + " 至 " + saved.assigneeId(), todo, saved);
        return WorkflowTodoResponse.from(saved);
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
        syncClinicalEventNotifications(ctx);

        WorkflowNotificationFilter safeFilter = filter == null
            ? new WorkflowNotificationFilter(null, null, null)
            : filter;
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        String recipientId = blankToNull(safeFilter.recipientId());
        String selectedOrgUnitId = blankToNull(safeFilter.orgUnitId());
        String currentUserId = currentUserId(ctx);
        String currentOrgUnitId = currentOrgUnitId(ctx);
        long total = selectedOrgUnitId == null
            ? notifications.countByVisibleRecipientScope(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.level()),
                recipientId,
                currentUserId,
                currentOrgUnitId)
            : notifications.countByVisibleRecipientScopeAndOrgUnitFilter(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.level()),
                recipientId,
                currentUserId,
                currentOrgUnitId,
                selectedOrgUnitId);
        List<WorkflowNotification> page = selectedOrgUnitId == null
            ? notifications.pageByVisibleRecipientScope(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.level()),
                recipientId,
                currentUserId,
                currentOrgUnitId,
                req.offset(),
                req.safeSize())
            : notifications.pageByVisibleRecipientScopeAndOrgUnitFilter(
                tenantId,
                name(safeFilter.status()),
                name(safeFilter.level()),
                recipientId,
                currentUserId,
                currentOrgUnitId,
                selectedOrgUnitId,
                req.offset(),
                req.safeSize());
        List<WorkflowNotificationResponse> rows = page.stream()
            .map(this::notificationResponse)
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
        WorkflowNotification notification = notifications.findVisibleByTenantIdAndNotificationId(
                tenantId,
                notificationId,
                currentUserId(ctx),
                currentOrgUnitId(ctx))
            .orElseThrow(() -> ApiException.notFound("通知"));
        WorkflowNotification read = new WorkflowNotification(
            notification.id(),
            notification.notificationId(),
            notification.tenantId(),
            notification.orgUnitId(),
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
        WorkflowNotification saved = notifications.save(read);
        recordNotificationAudit("标记通知已读 " + saved.notificationId(), notification, saved);
        return notificationResponse(saved);
    }

    private WorkflowNotificationResponse notificationResponse(WorkflowNotification notification) {
        return WorkflowNotificationResponse.from(notification, externalDeliveryStatuses(notification));
    }

    private List<WorkflowNotificationDeliveryResponse> externalDeliveryStatuses(
            WorkflowNotification notification) {
        return EXTERNAL_NOTIFICATION_CHANNELS.stream()
            .map(channel -> integrationLogs.findByMessageIdAndTenantId(
                    externalNotificationMessageId(notification, channel),
                    notification.tenantId())
                .map(log -> WorkflowNotificationDeliveryResponse.from(channel.code(), channel.targetSystem(), log)))
            .flatMap(Optional::stream)
            .toList();
    }

    private void recordTodoAudit(String summary, WorkflowTodo before, WorkflowTodo after) {
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.UPDATE,
            "workflow_todo",
            after.todoId(),
            summary,
            todoAuditSnapshot(before),
            todoAuditSnapshot(after),
            null));
    }

    private static Map<String, Object> todoAuditSnapshot(WorkflowTodo todo) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("todoId", todo.todoId());
        snapshot.put("orgUnitId", todo.orgUnitId());
        snapshot.put("sourceType", name(todo.sourceType()));
        snapshot.put("sourceId", todo.sourceId());
        snapshot.put("priority", name(todo.priority()));
        snapshot.put("status", name(todo.status()));
        snapshot.put("assigneeId", todo.assigneeId());
        snapshot.put("assigneeRole", todo.assigneeRole());
        snapshot.put("completedBy", todo.completedBy());
        snapshot.put("completionReasonProvided", blankToNull(todo.completionReason()) != null);
        snapshot.put("transferredTo", todo.transferredTo());
        snapshot.put("transferReason", todo.transferReason());
        snapshot.put("traceId", todo.traceId());
        return snapshot;
    }

    private void recordNotificationAudit(
            String summary,
            WorkflowNotification before,
            WorkflowNotification after) {
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.UPDATE,
            "workflow_notification",
            after.notificationId(),
            summary,
            notificationAuditSnapshot(before),
            notificationAuditSnapshot(after),
            null));
    }

    private static Map<String, Object> notificationAuditSnapshot(WorkflowNotification notification) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("notificationId", notification.notificationId());
        snapshot.put("orgUnitId", notification.orgUnitId());
        snapshot.put("sourceType", name(notification.sourceType()));
        snapshot.put("sourceId", notification.sourceId());
        snapshot.put("level", name(notification.level()));
        snapshot.put("status", name(notification.status()));
        snapshot.put("recipientId", notification.recipientId());
        snapshot.put("recipientRole", notification.recipientRole());
        snapshot.put("readBy", notification.readBy());
        snapshot.put("traceId", notification.traceId());
        return snapshot;
    }

    private void syncFollowupTodos(RequestContext.Snapshot ctx) {
        String tenantId = ctx.orgScope().tenantId();
        List<FollowupWorkflowTodoRow> rows = nullToEmpty(
            followupTasks.pageOpenWorkflowRows(tenantId, 0, SYNC_BATCH_SIZE));
        for (FollowupWorkflowTodoRow row : rows) {
            WorkflowTodo todo = todos.findByTenantIdAndSourceTypeAndSourceId(
                    tenantId,
                    WorkflowTodoSourceType.FOLLOWUP_TASK,
                    row.taskId())
                .orElseGet(() -> todos.save(fromFollowupTodo(ctx, row)));
            createPendingTodoNotificationIfAbsent(ctx, todo);
        }
    }

    private void syncSafetyTodos(RequestContext.Snapshot ctx) {
        String tenantId = ctx.orgScope().tenantId();
        List<AffectedCaseTask> rows = nullToEmpty(affectedTasks.pageByTenantId(tenantId, 0, SYNC_BATCH_SIZE));
        for (AffectedCaseTask task : rows) {
            if (!isOpenSafetyTask(task)) {
                continue;
            }
            WorkflowTodo todo = todos.findByTenantIdAndSourceTypeAndSourceId(
                    tenantId,
                    WorkflowTodoSourceType.SAFETY_REVIEW,
                    task.taskKey())
                .orElseGet(() -> todos.save(fromSafetyTask(ctx, task)));
            createPendingTodoNotificationIfAbsent(ctx, todo);
        }
    }

    private void syncRecommendationTodos(RequestContext.Snapshot ctx) {
        String tenantId = ctx.orgScope().tenantId();
        List<RecommendationWorkflowTodoRow> rows = nullToEmpty(
            recommendationCards.pageOpenWorkflowRows(tenantId, 0, SYNC_BATCH_SIZE));
        for (RecommendationWorkflowTodoRow row : rows) {
            WorkflowTodoSourceType sourceType = recommendationSourceType(row);
            var existing = todos.findByTenantIdAndSourceTypeAndSourceId(tenantId, sourceType, row.cardId());
            if (existing.isEmpty() && sourceType != WorkflowTodoSourceType.RECOMMENDATION_CARD) {
                existing = todos.findRecommendationDerivedByTenantIdAndSourceId(tenantId, row.cardId());
            }
            WorkflowTodo todo = existing.orElseGet(
                () -> todos.save(fromRecommendationCardTodo(ctx, row, sourceType)));
            createPendingTodoNotificationIfAbsent(ctx, todo);
        }
    }

    private void syncFollowupNotifications(RequestContext.Snapshot ctx) {
        String tenantId = ctx.orgScope().tenantId();
        List<FollowupNotificationRow> rows = nullToEmpty(
            followupEvents.pageNotificationRows(tenantId, 0, SYNC_BATCH_SIZE));
        for (FollowupNotificationRow row : rows) {
            String dedupeKey = "followup:" + row.eventId();
            if (notifications.findByTenantIdAndDedupeKey(tenantId, dedupeKey).isEmpty()) {
                WorkflowNotification saved = notifications.save(fromFollowupNotification(ctx, row, dedupeKey));
                enqueueExternalNotificationIfNeeded(ctx, saved);
            }
        }
    }

    private void syncClinicalEventNotifications(RequestContext.Snapshot ctx) {
        String tenantId = ctx.orgScope().tenantId();
        List<ClinicalEvent> rows = nullToEmpty(clinicalEvents.pageByFilter(
            tenantId, null, null, ClinicalEventStatus.PROCESSED.name(), null, 0, SYNC_BATCH_SIZE));
        for (ClinicalEvent event : rows) {
            String dedupeKey = "clinical-event:" + event.eventId();
            notifications.findByTenantIdAndDedupeKey(tenantId, dedupeKey)
                .orElseGet(() -> notifications.save(fromClinicalEventNotification(ctx, event, dedupeKey)));
        }
    }

    private WorkflowTodo fromFollowupTodo(RequestContext.Snapshot ctx, FollowupWorkflowTodoRow row) {
        Instant createdAt = row.createdAt() == null ? Instant.now() : row.createdAt();
        return new WorkflowTodo(
            null,
            "todo-" + UUID.randomUUID(),
            ctx.orgScope().tenantId(),
            currentOrgUnitId(ctx),
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

    private WorkflowTodo fromSafetyTask(RequestContext.Snapshot ctx, AffectedCaseTask task) {
        Instant createdAt = task.createdAt() == null ? Instant.now() : task.createdAt();
        String patientId = task.targetType() == AffectedCaseTargetType.PATIENT_CASE
            || task.targetType() == AffectedCaseTargetType.PATIENT_PATHWAY
            ? task.targetRef()
            : null;
        return new WorkflowTodo(
            null,
            "todo-" + UUID.randomUUID(),
            task.tenantId(),
            currentOrgUnitId(ctx),
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

    private WorkflowTodo fromRecommendationCardTodo(
            RequestContext.Snapshot ctx,
            RecommendationWorkflowTodoRow row,
            WorkflowTodoSourceType sourceType) {
        Instant createdAt = row.createdAt() == null ? Instant.now() : row.createdAt();
        return new WorkflowTodo(
            null,
            "todo-" + UUID.randomUUID(),
            ctx.orgScope().tenantId(),
            currentOrgUnitId(ctx),
            sourceType,
            row.cardId(),
            defaultText(row.title(), recommendationFallbackTitle(sourceType)),
            recommendationSummary(row),
            recommendationPriority(row.riskLevel()),
            WorkflowTodoStatus.PENDING,
            null,
            recommendationAssigneeRole(sourceType),
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
            currentOrgUnitId(ctx),
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

    private WorkflowNotification fromClinicalEventNotification(
            RequestContext.Snapshot ctx,
            ClinicalEvent event,
            String dedupeKey) {
        Instant createdAt = event.receivedAt() == null ? Instant.now() : event.receivedAt();
        String sourceSystem = defaultText(event.sourceSystem(), "院内系统");
        return new WorkflowNotification(
            null,
            "notify-" + UUID.randomUUID(),
            ctx.orgScope().tenantId(),
            currentOrgUnitId(ctx),
            WorkflowNotificationSourceType.SYNC_EVENT,
            event.eventId(),
            dedupeKey,
            "临床同步事件已处理",
            sourceSystem + " 的" + eventTriggerText(event) + "已进入临床事件引擎并完成处理",
            WorkflowNotificationLevel.INFO,
            WorkflowNotificationStatus.UNREAD,
            null,
            null,
            event.patientId(),
            event.encounterId(),
            "/rule/validate?eventId=" + event.eventId(),
            null,
            null,
            defaultText(event.traceId(), ctx.traceId()),
            createdAt,
            SYSTEM_ACTOR,
            createdAt,
            SYSTEM_ACTOR);
    }

    private void createTransferNotificationIfAbsent(
            RequestContext.Snapshot ctx,
            WorkflowTodo todo,
            String transferReason,
            Instant now,
            String actor) {
        String dedupeKey = "todo:" + todo.todoId() + ":transferred:" + todo.assigneeId();
        if (notifications.findByTenantIdAndDedupeKey(todo.tenantId(), dedupeKey).isPresent()) {
            return;
        }
        WorkflowNotification saved = notifications.save(new WorkflowNotification(
                null,
                "notify-" + UUID.randomUUID(),
                todo.tenantId(),
                todo.orgUnitId(),
                WorkflowNotificationSourceType.WORKFLOW_TODO,
                todo.todoId(),
                dedupeKey,
                "待办已转交",
                "待办「" + todo.title() + "」已转交给 " + todo.assigneeId() + "；转交说明：" + transferReason,
                notificationLevel(todo.priority()),
                WorkflowNotificationStatus.UNREAD,
                todo.assigneeId(),
                todo.assigneeRole(),
                todo.patientId(),
                todo.encounterId(),
                todo.deepLink(),
                null,
                null,
                ctx.traceId(),
                now,
                actor,
                now,
                actor));
        enqueueExternalNotificationIfNeeded(ctx, saved);
    }

    private void createPendingTodoNotificationIfAbsent(RequestContext.Snapshot ctx, WorkflowTodo todo) {
        if (!isOpenWorkflowTodo(todo)) {
            return;
        }
        String dedupeKey = "todo:" + todo.todoId() + ":created";
        if (notifications.findByTenantIdAndDedupeKey(todo.tenantId(), dedupeKey).isPresent()) {
            return;
        }
        Instant createdAt = todo.createdAt() == null ? Instant.now() : todo.createdAt();
        WorkflowNotification saved = notifications.save(new WorkflowNotification(
                null,
                "notify-" + UUID.randomUUID(),
                todo.tenantId(),
                todo.orgUnitId(),
                WorkflowNotificationSourceType.WORKFLOW_TODO,
                todo.todoId(),
                dedupeKey,
                "待办待处理",
                "待办「" + todo.title() + "」待处理，请按截止时间处理。",
                notificationLevel(todo.priority()),
                WorkflowNotificationStatus.UNREAD,
                blankToNull(todo.assigneeId()),
                todo.assigneeRole(),
                todo.patientId(),
                todo.encounterId(),
                todo.deepLink(),
                null,
                null,
                defaultText(todo.traceId(), ctx.traceId()),
                createdAt,
                defaultText(todo.createdBy(), SYSTEM_ACTOR),
                createdAt,
                defaultText(todo.createdBy(), SYSTEM_ACTOR)));
        enqueueExternalNotificationIfNeeded(ctx, saved);
    }

    private void createCompletionNotificationIfAbsent(
            RequestContext.Snapshot ctx,
            WorkflowTodo todo,
            String completionReason,
            Instant now,
            String actor) {
        String dedupeKey = "todo:" + todo.todoId() + ":completed";
        if (notifications.findByTenantIdAndDedupeKey(todo.tenantId(), dedupeKey).isPresent()) {
            return;
        }
        WorkflowNotification saved = notifications.save(new WorkflowNotification(
                null,
                "notify-" + UUID.randomUUID(),
                todo.tenantId(),
                todo.orgUnitId(),
                WorkflowNotificationSourceType.WORKFLOW_TODO,
                todo.todoId(),
                dedupeKey,
                "待办已完成",
                "待办「" + todo.title() + "」已完成；完成说明：" + completionReason,
                WorkflowNotificationLevel.INFO,
                WorkflowNotificationStatus.UNREAD,
                defaultText(todo.assigneeId(), actor),
                todo.assigneeRole(),
                todo.patientId(),
                todo.encounterId(),
                todo.deepLink(),
                null,
                null,
                ctx.traceId(),
                now,
                actor,
                now,
                actor));
        enqueueExternalNotificationIfNeeded(ctx, saved);
    }

    private void enqueueExternalNotificationIfNeeded(RequestContext.Snapshot ctx, WorkflowNotification notification) {
        String recipientId = blankToNull(notification.recipientId());
        if (recipientId == null) {
            return;
        }
        WorkflowNotificationSettingsResponse settings =
            notificationSettings.getSettingsForUser(notification.tenantId(), recipientId);
        if (settings == null
            || !notificationSettings.isSubscribed(notification.sourceType(), notification.level(), settings)
            || notificationSettings.isMutedByQuietHours(notification.level(), settings, LocalTime.now())) {
            return;
        }
        for (ExternalNotificationChannel channel : EXTERNAL_NOTIFICATION_CHANNELS) {
            if (isExternalChannelEnabled(settings, channel)) {
                integrationService.enqueueOutboundMessage(
                    notification.tenantId(),
                    externalNotificationRequest(ctx, notification, channel, recipientId));
            }
        }
    }

    private IntegrationOutboundRequestDto externalNotificationRequest(
            RequestContext.Snapshot ctx,
            WorkflowNotification notification,
            ExternalNotificationChannel channel,
            String recipientId) {
        String traceId = defaultText(notification.traceId(), ctx.traceId());
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        putIfPresent(payload, "notificationId", notification.notificationId());
        payload.put("channel", channel.code());
        payload.put("sourceType", notification.sourceType().name());
        putIfPresent(payload, "sourceId", notification.sourceId());
        payload.put("level", notification.level().name());
        payload.put("recipientId", recipientId);
        putIfPresent(payload, "recipientRole", notification.recipientRole());
        putIfPresent(payload, "deepLink", notification.deepLink());
        putIfPresent(payload, "traceId", traceId);
        return new IntegrationOutboundRequestDto(
            externalNotificationMessageId(notification, channel),
            traceId,
            channel.adapterId(),
            channel.targetSystem(),
            channel.protocolType(),
            truncate("通知外发补偿（" + channel.targetSystem() + "）：" + notification.title(), 512),
            payload,
            3);
    }

    private static boolean isExternalChannelEnabled(
            WorkflowNotificationSettingsResponse settings,
            ExternalNotificationChannel channel) {
        return switch (channel.code()) {
            case "sms" -> settings.smsEnabled();
            case "email" -> settings.emailEnabled();
            case "push" -> settings.pushEnabled();
            case "webhook" -> settings.webhookEnabled();
            case "in-hospital" -> settings.inHospitalMessageEnabled();
            default -> false;
        };
    }

    private static String externalNotificationMessageId(
            WorkflowNotification notification,
            ExternalNotificationChannel channel) {
        String prefix = "notify-out-" + channel.code() + "-";
        String source = defaultText(notification.notificationId(), UUID.randomUUID().toString());
        int maxSuffixLength = 64 - prefix.length();
        if (source.length() > maxSuffixLength) {
            source = source.substring(source.length() - maxSuffixLength);
        }
        return prefix + source;
    }

    private static void putIfPresent(ObjectNode payload, String field, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            payload.put(field, normalized);
        }
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

    private static boolean isOpenWorkflowTodo(WorkflowTodo todo) {
        return todo.status() == WorkflowTodoStatus.PENDING || todo.status() == WorkflowTodoStatus.IN_PROGRESS;
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

    private static WorkflowTodoSourceType recommendationSourceType(RecommendationWorkflowTodoRow row) {
        RecommendationCardType cardType = row.cardType();
        if (cardType == RecommendationCardType.NURSING) {
            return WorkflowTodoSourceType.NURSING_TASK;
        }
        if (cardType == RecommendationCardType.KNOWLEDGE) {
            return WorkflowTodoSourceType.BEDSIDE_KNOWLEDGE;
        }
        if ((cardType == RecommendationCardType.EXAM || cardType == RecommendationCardType.LAB)
            && isReportContext(row)) {
            return WorkflowTodoSourceType.REPORT_INTERPRETATION;
        }
        return WorkflowTodoSourceType.RECOMMENDATION_CARD;
    }

    private static boolean isReportContext(RecommendationWorkflowTodoRow row) {
        return containsReportSignal(row.triggerType()) || containsReportSignal(row.scenarioCode());
    }

    private static boolean containsReportSignal(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return false;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return upper.contains("REPORT")
            || upper.contains("RESULT_REVIEW")
            || upper.contains("DIAGNOSTIC");
    }

    private static String recommendationAssigneeRole(WorkflowTodoSourceType sourceType) {
        return sourceType == WorkflowTodoSourceType.NURSING_TASK ? "NURSING" : "DOCTOR";
    }

    private static String recommendationFallbackTitle(WorkflowTodoSourceType sourceType) {
        return switch (sourceType) {
            case NURSING_TASK -> "护理协同任务";
            case REPORT_INTERPRETATION -> "报告解读复核";
            case BEDSIDE_KNOWLEDGE -> "床旁知识卡复核";
            case FOLLOWUP_TASK, SAFETY_REVIEW, RECOMMENDATION_CARD -> "临床提醒复核";
        };
    }

    private static WorkflowNotificationLevel notificationLevel(WorkflowPriority priority) {
        return switch (priority) {
            case CRITICAL -> WorkflowNotificationLevel.CRITICAL;
            case HIGH -> WorkflowNotificationLevel.HIGH;
            case LOW -> WorkflowNotificationLevel.LOW;
            case MEDIUM -> WorkflowNotificationLevel.MEDIUM;
        };
    }

    private static String eventTriggerText(ClinicalEvent event) {
        ClinicalEventTriggerPoint triggerPoint = event.triggerPoint();
        if (triggerPoint == null) {
            return "临床事件";
        }
        return switch (triggerPoint) {
            case PATIENT_VIEW -> "患者查看事件";
            case ORDER_SIGN -> "医嘱签署事件";
            case MEDICATION_PRESCRIBE -> "用药开立事件";
            case RESULT_REVIEW -> "报告查看事件";
            case DISCHARGE_SIGN -> "出院签署事件";
            case FOLLOWUP_ALERT -> "随访提醒事件";
        };
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

    private static String currentUserId(RequestContext.Snapshot ctx) {
        return blankToNull(ctx.userId());
    }

    private static String currentOrgUnitId(RequestContext.Snapshot ctx) {
        OrgScope scope = ctx.orgScope();
        String orgUnitId = blankToNull(scope.specialtyId());
        if (orgUnitId != null) {
            return orgUnitId;
        }
        orgUnitId = blankToNull(scope.departmentId());
        if (orgUnitId != null) {
            return orgUnitId;
        }
        orgUnitId = blankToNull(scope.siteId());
        if (orgUnitId != null) {
            return orgUnitId;
        }
        orgUnitId = blankToNull(scope.campusId());
        if (orgUnitId != null) {
            return orgUnitId;
        }
        orgUnitId = blankToNull(scope.hospitalId());
        if (orgUnitId != null) {
            return orgUnitId;
        }
        return blankToNull(scope.groupId());
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

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private record ExternalNotificationChannel(
        String code,
        String adapterId,
        String targetSystem,
        String protocolType
    ) {
    }
}
