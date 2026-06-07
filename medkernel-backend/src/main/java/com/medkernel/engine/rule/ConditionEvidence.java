package com.medkernel.engine.rule;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 条件叶子求值证据。
 */
public record ConditionEvidence(
    String fact,
    String sourcePath,
    String operator,
    JsonNode expected,
    JsonNode actual,
    boolean matched,
    boolean missing,
    JsonNode value,
    String unit,
    String source,
    String formula
) {
}
