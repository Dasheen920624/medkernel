package com.medkernel.engine.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建评估指标草稿版本的请求。
 *
 * <p>包含指标编码、评估对象类型、分母/分子定义、适用范围、责任科室和来源引用。
 * 内部业务版本由服务端按同编码历史版本自动递增。
 */
public record EvaluationIndicatorCreateRequest(
    @NotBlank String indicatorCode,
    @NotBlank String name,
    @NotNull EvaluationSubjectType subjectType,
    @NotBlank String denominatorDefinition,
    @NotBlank String numeratorDefinition,
    String exclusionDefinition,
    String scoringDefinition,
    @NotBlank String timeWindow,
    @NotBlank String organizationScope,
    @NotBlank String responsibleDepartmentId,
    @NotBlank String sourceRef
) {}
