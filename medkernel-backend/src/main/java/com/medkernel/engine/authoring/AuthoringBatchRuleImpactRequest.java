package com.medkernel.engine.authoring;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 规则批量发布影响分析请求。
 */
public record AuthoringBatchRuleImpactRequest(
    @NotEmpty @Size(max = 200) List<String> ruleIds
) {
    public AuthoringBatchRuleImpactRequest {
        ruleIds = ruleIds == null ? null : List.copyOf(ruleIds);
    }
}
