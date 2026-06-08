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
    String formula,
    String errorCode,
    String errorMessage
) {
    public ConditionEvidence(
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
        this(fact, sourcePath, operator, expected, actual, matched, missing, value, unit, source, formula, null, null);
    }
}
