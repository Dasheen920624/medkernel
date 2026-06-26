package com.medkernel.engine.llm;

import jakarta.validation.constraints.NotBlank;

/**
 * 模型赋能覆盖矩阵业务点登记/更新请求（LLM-05）。
 *
 * <p>{@code capabilityCode}/{@code b0Path} 在 {@code PENDING}（待配置）时可空；{@code ACTIVE}（上线）
 * 时二者必填且能力码须已在网关登记，否则被 {@code ENG-LLM-010}/{@code ENG-LLM-001} 阻断。
 */
public record ModelEnhancementMatrixUpsertRequest(
    @NotBlank String businessName,
    String capabilityCode,
    String b0Path,
    @NotBlank String accessStatus,
    Boolean enabled,
    Integer sortOrder
) {}
