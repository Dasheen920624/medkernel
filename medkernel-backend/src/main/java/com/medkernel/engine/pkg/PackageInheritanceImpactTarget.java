package com.medkernel.engine.pkg;

/**
 * 单个组织单元在平台上游版本变更下的继承影响。
 */
public record PackageInheritanceImpactTarget(
    String orgUnitId,
    String orgPath,
    PackageInheritanceImpactType impactType,
    String effectiveVersionId,
    String effectiveVersionNo,
    String sourceTier,
    String diffSummary,
    String rebasePrompt
) {}
