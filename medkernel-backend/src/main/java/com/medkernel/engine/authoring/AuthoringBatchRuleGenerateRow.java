package com.medkernel.engine.authoring;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 模板参数表中的一行规则生成参数。
 */
public record AuthoringBatchRuleGenerateRow(
    @NotBlank String rowId,
    @NotBlank String ruleCode,
    @NotBlank String name,
    @NotNull JsonNode parameterBindings,
    String packageVersion,
    String applicableOrgUnitId,
    String changeSummary
) {}
