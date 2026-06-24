package com.medkernel.engine.evaluation;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 创建评估运行事实的请求。
 *
 * <p>必须包含运行编码、运行类型、场景编码、输入摘要和至少一条评估结果，并提供可追溯上下文或人工抽检来源。
 */
public record EvaluationRunRequest(
    @NotBlank String runCode,
    @NotNull EvaluationRunType runType,
    String sourceEventId,
    String contextSnapshotId,
    String patientId,
    String encounterId,
    @NotBlank String scenarioCode,
    String runtimeReleaseId,
    @NotBlank String inputDigest,
    Instant occurredAt,
    @NotEmpty List<@Valid EvaluationResultRequest> results
) {}
