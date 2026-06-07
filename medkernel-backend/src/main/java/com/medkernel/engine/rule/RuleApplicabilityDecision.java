package com.medkernel.engine.rule;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 规则适用域判定结果。
 *
 * @param applicable 是否进入规则条件求值
 * @param reasonCode 稳定原因编码
 * @param reason      中文判定说明
 * @param details     判定维度证据
 */
public record RuleApplicabilityDecision(
    boolean applicable,
    String reasonCode,
    String reason,
    JsonNode details
) {}
