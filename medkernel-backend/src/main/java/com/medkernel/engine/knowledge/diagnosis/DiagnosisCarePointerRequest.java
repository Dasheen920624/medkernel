package com.medkernel.engine.knowledge.diagnosis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增诊疗指针请求：指向治疗/检查/路径（恒软建议，不自动执行）。
 */
public record DiagnosisCarePointerRequest(
    @NotNull DiagnosisCarePointerType pointerType,
    @NotBlank String targetRef,
    boolean isSoft,
    String description
) {}
