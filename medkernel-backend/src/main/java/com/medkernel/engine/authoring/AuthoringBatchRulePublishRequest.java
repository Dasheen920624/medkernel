package com.medkernel.engine.authoring;

import java.util.List;

import com.medkernel.engine.rule.RuleGovernanceState;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 规则批量治理推进请求。
 */
public record AuthoringBatchRulePublishRequest(
    @NotNull RuleGovernanceState targetState,
    @NotBlank @Size(max = 500) String reason,
    @NotEmpty @Size(max = 200) List<@Valid AuthoringBatchRulePublishItem> items
) {
    public AuthoringBatchRulePublishRequest {
        items = items == null ? null : List.copyOf(items);
    }
}
