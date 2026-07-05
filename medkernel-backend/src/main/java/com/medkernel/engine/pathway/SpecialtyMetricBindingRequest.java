package com.medkernel.engine.pathway;

import jakarta.validation.constraints.NotBlank;

/**
 * 专病指标绑定请求片段。
 *
 * <p>用于在创建临床路径时声明节点与评价指标的绑定关系和必填属性。
 */
public record SpecialtyMetricBindingRequest(
    @NotBlank String nodeCode,
    @NotBlank String metricCode,
    Boolean required
) {}
