package com.medkernel.engine.pathway;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 路径结局指标绑定请求片段。
 *
 * <p>指标编码必须引用当前租户已激活的 {@code EvaluationIndicator}。
 */
public record PathwayOutcomeBindingRequest(
    @NotNull PathwayOutcomeScope scope,
    String refCode,
    @NotBlank String indicatorCode
) {}
