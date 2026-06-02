package com.medkernel.engine.versioning;

import java.util.List;

/**
 * 继承解析解释，面向审核台和发布证据复用。
 */
public record InheritanceExplanation(
    String resolutionSummary,
    List<String> inheritancePath,
    String diffSummary,
    String overrideReason,
    String impactScope
) {
    public InheritanceExplanation {
        inheritancePath = inheritancePath == null ? List.of() : List.copyOf(inheritancePath);
    }
}
