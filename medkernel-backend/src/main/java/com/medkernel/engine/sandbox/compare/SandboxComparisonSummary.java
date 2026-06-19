package com.medkernel.engine.sandbox.compare;

/** 对比运行的可审计计数摘要。 */
public record SandboxComparisonSummary(
    int differenceCount,
    int newHitCount,
    int noLongerHitCount,
    int highRiskChangeCount,
    int nonComparableCount
) {}
