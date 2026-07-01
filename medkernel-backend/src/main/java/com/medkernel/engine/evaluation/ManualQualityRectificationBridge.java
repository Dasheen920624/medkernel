package com.medkernel.engine.evaluation;

import java.time.Instant;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 手工质控整改桥接服务。
 *
 * <p>外部业务域只提供已确认的问题事实；本服务在 evaluation owner 边界内创建
 * {@code quality_finding} 与 {@code rectification_task}，保证质控问题和整改任务的单一归属。
 */
@Service
public class ManualQualityRectificationBridge {
    private final QualityFindingRepository findings;
    private final RectificationTaskRepository tasks;

    public ManualQualityRectificationBridge(
            QualityFindingRepository findings,
            RectificationTaskRepository tasks) {
        this.findings = findings;
        this.tasks = tasks;
    }

    /**
     * 幂等创建手工质控问题和对应整改任务。
     */
    public ManualQualityRectificationResult ensureAssignedTask(ManualQualityRectificationCommand command) {
        requireCommand(command);
        boolean findingCreated = false;
        boolean taskCreated = false;
        if (findings.findByFindingIdAndTenantId(command.findingId(), command.tenantId()).isEmpty()) {
            findings.save(new QualityFinding(
                null,
                command.findingId(),
                command.tenantId(),
                command.runId(),
                command.resultId(),
                command.indicatorId(),
                command.findingCode(),
                command.title(),
                command.description(),
                command.severity(),
                QualityFindingStatus.ASSIGNED,
                command.evidenceSummary(),
                command.responsibleDepartmentId(),
                command.dueAt(),
                command.now(),
                command.actor(),
                command.now(),
                command.actor(),
                command.traceId()));
            findingCreated = true;
        }
        if (tasks.findByTaskIdAndTenantId(command.taskId(), command.tenantId()).isEmpty()) {
            tasks.save(new RectificationTask(
                null,
                command.taskId(),
                command.tenantId(),
                command.findingId(),
                command.responsibleDepartmentId(),
                command.assigneeUserId(),
                RectificationTaskStatus.ASSIGNED,
                command.dueAt(),
                null,
                null,
                null,
                null,
                null,
                command.now(),
                command.actor(),
                command.now(),
                command.actor(),
                command.traceId()));
            taskCreated = true;
        }
        return new ManualQualityRectificationResult(
            command.findingId(), command.taskId(), findingCreated, taskCreated);
    }

    private void requireCommand(ManualQualityRectificationCommand command) {
        if (command == null
                || !hasText(command.tenantId())
                || !hasText(command.findingId())
                || !hasText(command.taskId())
                || !hasText(command.runId())
                || !hasText(command.resultId())
                || !hasText(command.indicatorId())
                || !hasText(command.findingCode())
                || !hasText(command.title())
                || !hasText(command.description())
                || command.severity() == null
                || !hasText(command.evidenceSummary())
                || !hasText(command.responsibleDepartmentId())
                || command.dueAt() == null
                || command.now() == null
                || !hasText(command.actor())
                || !hasText(command.traceId())) {
            throw new ApiException(ErrorCode.ENG_EVAL_001, "手工质控整改命令缺少必要字段");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 手工质控整改所需的最小事实。
     */
    public record ManualQualityRectificationCommand(
        String tenantId,
        String findingId,
        String taskId,
        String runId,
        String resultId,
        String indicatorId,
        String findingCode,
        String title,
        String description,
        QualityFindingSeverity severity,
        String evidenceSummary,
        String responsibleDepartmentId,
        String assigneeUserId,
        Instant dueAt,
        Instant now,
        String actor,
        String traceId
    ) {
    }

    /**
     * 手工质控整改幂等创建结果。
     */
    public record ManualQualityRectificationResult(
        String findingId,
        String taskId,
        boolean findingCreated,
        boolean taskCreated
    ) {
    }
}
