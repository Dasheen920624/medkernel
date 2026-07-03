package com.medkernel.engine.authoring;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 基于基准规则和参数表批量生成规则请求。
 */
public record AuthoringBatchRuleGenerateRequest(
    @NotBlank String templateRuleId,
    @NotEmpty @Size(max = 500) List<@Valid AuthoringBatchRuleGenerateRow> rows
) {
    public AuthoringBatchRuleGenerateRequest {
        rows = rows == null ? null : List.copyOf(rows);
    }
}
