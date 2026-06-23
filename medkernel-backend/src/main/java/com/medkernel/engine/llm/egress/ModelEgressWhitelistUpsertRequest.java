package com.medkernel.engine.llm.egress;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

/**
 * 出域白名单与数据最小化策略维护请求（LLM-03 / OPT-09）。
 */
public record ModelEgressWhitelistUpsertRequest(
    @NotEmpty List<String> allowedFields,
    @NotBlank String sensitivityLevel,
    Map<String, String> desensitizationRules,
    String confirmationThresholdLevel
) {

    public ModelEgressWhitelistUpsertRequest(List<String> allowedFields, String sensitivityLevel) {
        this(allowedFields, sensitivityLevel, Map.of(), "HIGH");
    }

    public ModelEgressWhitelistUpsertRequest {
        desensitizationRules = desensitizationRules == null ? Map.of() : desensitizationRules;
        confirmationThresholdLevel = confirmationThresholdLevel == null || confirmationThresholdLevel.isBlank()
            ? "HIGH" : confirmationThresholdLevel;
    }
}
