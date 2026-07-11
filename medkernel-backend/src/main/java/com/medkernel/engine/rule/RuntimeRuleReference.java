package com.medkernel.engine.rule;

/** 机构生效版本锁定的一条可执行规则及其确切内容版本。 */
public record RuntimeRuleReference(
    String tenantId,
    String ruleId,
    String versionId,
    String assetVersionId,
    String assetVersionNo,
    String contentHash,
    String sourceLayer
) {
    public RuntimeRuleReference(String tenantId, String ruleId, String versionId) {
        this(tenantId, ruleId, versionId, null, null, null, null);
    }
}
