package com.medkernel.engine.workflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.medkernel.engine.event.ClockSlaBreachedEvent;
import com.medkernel.engine.event.EngineDomainEventPort;
import com.medkernel.engine.event.OverrideCapturedEvent;
import com.medkernel.engine.event.PathwayVarianceRecordedEvent;
import com.medkernel.engine.event.RuleFiredEvent;
import com.medkernel.engine.quality.dashboard.QualityDashboardAlert;
import com.medkernel.engine.quality.dashboard.QualityDashboardAlertRepository;
import com.medkernel.engine.quality.dashboard.QualityDashboardAlertStatus;
import com.medkernel.engine.quality.dashboard.QualityDashboardAlertType;
import org.springframework.stereotype.Component;

/**
 * 引擎领域事件到协同中心与质控驾驶舱的统一适配器。
 */
@Component
public class EngineWorkflowDomainEventAdapter implements EngineDomainEventPort {

    private static final String SYSTEM_ACTOR = "engine-event";

    private final WorkflowTodoRepository todos;
    private final WorkflowNotificationRepository notifications;
    private final QualityDashboardAlertRepository alerts;

    public EngineWorkflowDomainEventAdapter(
            WorkflowTodoRepository todos,
            WorkflowNotificationRepository notifications,
            QualityDashboardAlertRepository alerts) {
        this.todos = todos;
        this.notifications = notifications;
        this.alerts = alerts;
    }

    @Override
    public void ruleFired(RuleFiredEvent event) {
        saveNotificationIfAbsent(
            event.tenantId(),
            WorkflowNotificationSourceType.RULE_EVENT,
            event.executionId(),
            "rule-fired:" + event.executionId(),
            "规则已命中",
            runtimeReleaseLabel(event.runtimeReleaseId()) + " 的规则 " + event.ruleCode()
                + " 已命中，动作 " + actionSummary(event.actions()),
            levelForSeverity(event.severity()),
            null,
            event.patientId(),
            event.encounterId(),
            "/clinical/rules/executions/" + event.executionId(),
            event.traceId(),
            event.occurredAt());
    }

    @Override
    public void overrideCaptured(OverrideCapturedEvent event) {
        saveQualityAlert(
            event.tenantId(),
            QualityDashboardAlertType.RULE_OVERRIDE,
            "rule_override_log",
            event.overrideId(),
            severityForRuleAction(event.actionCode()),
            "RULE_OVERRIDE",
            "规则人工越权已记录",
            "执行 " + event.executionId() + " / " + runtimeReleaseLabel(event.runtimeReleaseId())
                + " / 规则 " + event.ruleCode() + " / 动作 " + event.actionCode()
                + " / 越权人 " + event.overriddenBy(),
            event.traceId(),
            event.occurredAt());
    }

    @Override
    public void pathwayVarianceRecorded(PathwayVarianceRecordedEvent event) {
        saveTodoIfAbsent(
            event.tenantId(),
            WorkflowTodoSourceType.PATHWAY_EVENT,
            event.varianceId(),
            "路径变异待处理",
            "运行修订 " + event.runtimeReleaseId() + " / 节点 " + event.nodeCode()
                + " / 原因 " + nullToDash(event.reasonCode()) + " / 决策 " + event.resolutionDecision(),
            priorityForDecision(event.resolutionDecision()),
            null,
            event.responsibleRole(),
            event.patientId(),
            event.encounterId(),
            null,
            "/clinical/pathways?patientPathwayId=" + event.patientPathwayId()
                + "&varianceId=" + event.varianceId(),
            event.traceId(),
            event.occurredAt());
        saveNotificationIfAbsent(
            event.tenantId(),
            WorkflowNotificationSourceType.PATHWAY_EVENT,
            event.varianceId(),
            "pathway-variance:" + event.varianceId(),
            "路径变异已登记",
            "运行修订 " + event.runtimeReleaseId() + " 的患者路径 "
                + event.patientPathwayId() + " 已登记变异",
            WorkflowNotificationLevel.HIGH,
            event.responsibleRole(),
            event.patientId(),
            event.encounterId(),
            "/clinical/pathways?patientPathwayId=" + event.patientPathwayId()
                + "&varianceId=" + event.varianceId(),
            event.traceId(),
            event.occurredAt());
        saveQualityAlert(
            event.tenantId(),
            QualityDashboardAlertType.PATHWAY_VARIANCE,
            "pathway_variance",
            event.varianceId(),
            severityForDecision(event.resolutionDecision()),
            "PATHWAY_VARIANCE",
            "路径变异已登记",
            "运行修订 " + event.runtimeReleaseId() + " / 患者路径 " + event.patientPathwayId()
                + " / 节点 " + event.nodeCode() + " / 原因 " + nullToDash(event.reasonCode())
                + " / 决策 " + event.resolutionDecision(),
            event.traceId(),
            event.occurredAt());
    }

    @Override
    public void clockSlaBreached(ClockSlaBreachedEvent event) {
        saveTodoIfAbsent(
            event.tenantId(),
            WorkflowTodoSourceType.PATHWAY_EVENT,
            "clock-sla:" + event.clockId(),
            "路径时钟 SLA 已超时",
            "运行修订 " + event.runtimeReleaseId() + " / 节点 " + event.nodeCode()
                + " / 指标 " + nullToDash(event.metricCode()) + " / 升级 " + event.escalationLevel(),
            priorityForEscalation(event.escalationLevel()),
            null,
            null,
            event.patientId(),
            event.encounterId(),
            event.dueAt(),
            "/clinical/pathways?patientPathwayId=" + event.patientPathwayId()
                + "&clockId=" + event.clockId(),
            event.traceId(),
            event.occurredAt());
        saveNotificationIfAbsent(
            event.tenantId(),
            WorkflowNotificationSourceType.PATHWAY_EVENT,
            event.clockId(),
            "clock-sla:" + event.clockId(),
            "路径时钟 SLA 已超时",
            "运行修订 " + event.runtimeReleaseId() + " 的节点 " + event.nodeCode()
                + " 已升级至 " + event.escalationLevel(),
            levelForEscalation(event.escalationLevel()),
            null,
            event.patientId(),
            event.encounterId(),
            "/clinical/pathways?patientPathwayId=" + event.patientPathwayId()
                + "&clockId=" + event.clockId(),
            event.traceId(),
            event.occurredAt());
        saveQualityAlert(
            event.tenantId(),
            QualityDashboardAlertType.CLOCK_SLA_BREACH,
            "clinical_clock",
            event.clockId(),
            severityForEscalation(event.escalationLevel()),
            "CLOCK_SLA",
            "路径关键时钟 SLA 超时",
            "运行修订 " + event.runtimeReleaseId() + " / 患者路径 " + event.patientPathwayId()
                + " / 节点 " + event.nodeCode() + " / 指标 " + nullToDash(event.metricCode())
                + " / 升级 " + event.escalationLevel(),
            event.traceId(),
            event.occurredAt());
    }

    private void saveTodoIfAbsent(
            String tenantId,
            WorkflowTodoSourceType sourceType,
            String sourceId,
            String title,
            String summary,
            WorkflowPriority priority,
            String assigneeId,
            String assigneeRole,
            String patientId,
            String encounterId,
            Instant dueAt,
            String deepLink,
            String traceId,
            Instant now) {
        if (todos.findByTenantIdAndSourceTypeAndSourceId(tenantId, sourceType, sourceId).isPresent()) {
            return;
        }
        todos.save(new WorkflowTodo(
            null,
            "todo-" + UUID.randomUUID(),
            tenantId,
            sourceType,
            sourceId,
            title,
            summary,
            priority,
            WorkflowTodoStatus.PENDING,
            assigneeId,
            assigneeRole,
            patientId,
            encounterId,
            dueAt,
            deepLink,
            null,
            null,
            null,
            null,
            null,
            traceId,
            now,
            SYSTEM_ACTOR,
            now,
            SYSTEM_ACTOR));
    }

    private void saveNotificationIfAbsent(
            String tenantId,
            WorkflowNotificationSourceType sourceType,
            String sourceId,
            String dedupeKey,
            String title,
            String message,
            WorkflowNotificationLevel level,
            String recipientRole,
            String patientId,
            String encounterId,
            String deepLink,
            String traceId,
            Instant now) {
        if (notifications.findByTenantIdAndDedupeKey(tenantId, dedupeKey).isPresent()) {
            return;
        }
        notifications.save(new WorkflowNotification(
            null,
            "noti-" + UUID.randomUUID(),
            tenantId,
            sourceType,
            sourceId,
            dedupeKey,
            title,
            message,
            level,
            WorkflowNotificationStatus.UNREAD,
            null,
            recipientRole,
            patientId,
            encounterId,
            deepLink,
            null,
            null,
            traceId,
            now,
            SYSTEM_ACTOR,
            now,
            SYSTEM_ACTOR));
    }

    private void saveQualityAlert(
            String tenantId,
            QualityDashboardAlertType alertType,
            String sourceType,
            String sourceId,
            String severity,
            String thresholdCode,
            String title,
            String evidenceSummary,
            String traceId,
            Instant now) {
        QualityDashboardAlert existing = alerts.findByTenantIdAndAlertTypeAndSourceTypeAndSourceId(
                tenantId, alertType, sourceType, sourceId)
            .orElse(null);
        alerts.save(new QualityDashboardAlert(
            existing == null ? null : existing.id(),
            existing == null ? "qalert-" + UUID.randomUUID() : existing.alertId(),
            tenantId,
            existing == null ? null : existing.departmentId(),
            alertType,
            sourceType,
            sourceId,
            severity,
            existing == null ? QualityDashboardAlertStatus.OPEN : existing.status(),
            thresholdCode,
            existing == null ? BigDecimal.ZERO : existing.thresholdValue(),
            existing == null ? BigDecimal.ONE : existing.actualValue(),
            title,
            evidenceSummary,
            existing == null ? now : existing.createdAt(),
            existing == null ? SYSTEM_ACTOR : existing.createdBy(),
            now,
            SYSTEM_ACTOR,
            traceId));
    }

    private static String actionSummary(List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return "无";
        }
        return String.join(",", actions);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String runtimeReleaseLabel(String runtimeReleaseId) {
        return runtimeReleaseId == null || runtimeReleaseId.isBlank()
            ? "未绑定运行修订"
            : "运行修订 " + runtimeReleaseId;
    }

    private static String severityForRuleAction(String actionCode) {
        return "BLOCK".equals(actionCode) ? "P0" : "P1";
    }

    private static String severityForDecision(String decision) {
        return "TERMINATE".equals(decision) ? "P1" : "P2";
    }

    private static String severityForEscalation(String escalationLevel) {
        if ("QUALITY_RECORD".equals(escalationLevel) || "REPORT".equals(escalationLevel)) {
            return "P1";
        }
        if ("REMINDER".equals(escalationLevel)) {
            return "P2";
        }
        return "P3";
    }

    private static WorkflowPriority priorityForDecision(String decision) {
        return "TERMINATE".equals(decision) ? WorkflowPriority.CRITICAL : WorkflowPriority.HIGH;
    }

    private static WorkflowPriority priorityForEscalation(String escalationLevel) {
        return "QUALITY_RECORD".equals(escalationLevel) ? WorkflowPriority.CRITICAL : WorkflowPriority.HIGH;
    }

    private static WorkflowNotificationLevel levelForEscalation(String escalationLevel) {
        return "QUALITY_RECORD".equals(escalationLevel)
            ? WorkflowNotificationLevel.CRITICAL
            : WorkflowNotificationLevel.HIGH;
    }

    private static WorkflowNotificationLevel levelForSeverity(String severity) {
        if ("CRITICAL".equals(severity)) {
            return WorkflowNotificationLevel.CRITICAL;
        }
        if ("HIGH".equals(severity)) {
            return WorkflowNotificationLevel.HIGH;
        }
        if ("MEDIUM".equals(severity)) {
            return WorkflowNotificationLevel.MEDIUM;
        }
        if ("LOW".equals(severity)) {
            return WorkflowNotificationLevel.LOW;
        }
        return WorkflowNotificationLevel.INFO;
    }
}
