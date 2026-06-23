package com.medkernel.engine.rule;

/**
 * 规则下一版本创建结果。
 */
public record RuleVersionCreateResponse(
    String ruleId,
    String versionId,
    Integer versionNo,
    RuleVersionStatus status,
    String traceId
) {}
