package com.medkernel.engine.knowledge.diagnosis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增诊断标准请求：发现项标准编码 + 方向 + 权重；数值/时序约束可登记但 B0 发布前会被门禁阻断。
 */
public record DiagnosisCriterionRequest(
    @NotBlank String findingTermCode,
    @NotNull DiagnosisDirection direction,
    @NotNull DiagnosisWeight weight,
    String valueConstraint,
    String temporalConstraint,
    Long citationId
) {}
