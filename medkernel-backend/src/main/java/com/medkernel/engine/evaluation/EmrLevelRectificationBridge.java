package com.medkernel.engine.evaluation;

import java.time.Instant;

import org.springframework.stereotype.Service;

/**
 * EMR 评级差距联动质量问题与整改任务的 owner 内桥接服务。
 *
 * <p>电子病历评级模块只提供差距事实，本服务在 evaluation owner 边界内落库
 * {@code quality_finding} 与 {@code rectification_task}，保持表归属清晰。
 */
@Service
public class EmrLevelRectificationBridge {
    private final QualityFindingRepository findings;
    private final RectificationTaskRepository tasks;

    public EmrLevelRectificationBridge(
            QualityFindingRepository findings,
            RectificationTaskRepository tasks) {
        this.findings = findings;
        this.tasks = tasks;
    }

    /**
     * 确保 EMR 评级差距存在对应的质量问题和整改任务。
     */
    public void ensureTask(EmrLevelRectificationCommand command) {
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
                QualityFindingSeverity.P2,
                QualityFindingStatus.ASSIGNED,
                command.evidenceSummary(),
                command.responsibleDepartmentId(),
                command.dueAt(),
                command.now(),
                command.actor(),
                command.now(),
                command.actor(),
                command.traceId()));
        }
        if (tasks.findByTaskIdAndTenantId(command.taskId(), command.tenantId()).isEmpty()) {
            tasks.save(new RectificationTask(
                null,
                command.taskId(),
                command.tenantId(),
                command.findingId(),
                command.responsibleDepartmentId(),
                null,
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
        }
    }

    /**
     * EMR 评级差距派发整改所需的最小事实。
     */
    public record EmrLevelRectificationCommand(
        String tenantId,
        String findingId,
        String taskId,
        String runId,
        String resultId,
        String indicatorId,
        String findingCode,
        String title,
        String description,
        String evidenceSummary,
        String responsibleDepartmentId,
        Instant dueAt,
        Instant now,
        String actor,
        String traceId
    ) {
    }
}
