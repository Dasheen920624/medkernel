package com.medkernel.engine.sandbox;

/**
 * 全真体验沙盘内置场景定义。
 */
public record SandboxScenario(
    String id,
    String serviceLine,
    String engine,
    String triggerPoint,
    String ruleType,
    String title,
    String patientId,
    String encounterId,
    String expectedRuleCode,
    String expectedAction,
    String expectedSeverity,
    String playbook,
    String expectedAssetCode
) {
}
