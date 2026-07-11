package com.medkernel.engine.authoring;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.AssetTriggerBindingInput;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 批量参数表中的一行规则生成参数。
 */
public record AuthoringBatchRuleGenerateRow(
    @NotBlank String rowId,
    @NotBlank String ruleCode,
    @NotBlank String name,
    @NotNull JsonNode parameterBindings,
    List<AssetTriggerBindingInput> triggers,
    String applicableOrgUnitId,
    String changeSummary
) {}
