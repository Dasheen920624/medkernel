package com.medkernel.engine.rule;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 参与静态冲突比较的已发布规则快照。
 */
public record RuleConflictTarget(
    String ruleCode,
    JsonNode dsl
) {}
