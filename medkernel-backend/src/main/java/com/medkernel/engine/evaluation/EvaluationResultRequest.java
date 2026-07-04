package com.medkernel.engine.evaluation;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 评估运行中的单条结果请求。
 *
 * <p>绑定当前生效指标、评估对象、结果等级、证据摘要和可选质量问题；接口只接收受控事实，不自动计算结果。
 */
public record EvaluationResultRequest(
    @NotBlank String indicatorId,
    @NotNull EvaluationSubjectType subjectType,
    @NotBlank String subjectRefId,
    BigDecimal scoreValue,
    @NotNull EvaluationResultLevel resultLevel,
    boolean hitFlag,
    @NotBlank String evidenceSummary,
    String sourceRef,
    String responsibleDepartmentId,
    List<@Valid QualityFindingRequest> findings
) {}
