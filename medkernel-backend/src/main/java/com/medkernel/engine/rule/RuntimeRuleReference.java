package com.medkernel.engine.rule;

/** 机构生效版本锁定的一条可执行规则及其确切内容版本。 */
public record RuntimeRuleReference(
    String tenantId,
    String ruleId,
    String versionId
) {
}
