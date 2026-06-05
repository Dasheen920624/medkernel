package com.medkernel.compliance.masking;

import java.time.Instant;

/**
 * SYS-06 脱敏规则响应。
 */
public record MaskingRuleResponse(
    String ruleId,
    String tenantId,
    String resourceType,
    String fieldName,
    String scenarioCode,
    MaskingStrategy strategy,
    String maskChar,
    Integer prefixKeep,
    Integer suffixKeep,
    MaskingRuleStatus status,
    Long version,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    String traceId
) {

    static MaskingRuleResponse from(MaskingRule rule) {
        return new MaskingRuleResponse(
            rule.ruleId(),
            rule.tenantId(),
            rule.resourceType(),
            rule.fieldName(),
            rule.scenarioCode(),
            MaskingStrategy.valueOf(rule.strategy()),
            rule.maskChar(),
            rule.prefixKeep(),
            rule.suffixKeep(),
            MaskingRuleStatus.valueOf(rule.status()),
            rule.version(),
            rule.createdAt(),
            rule.createdBy(),
            rule.updatedAt(),
            rule.updatedBy(),
            rule.traceId());
    }
}
