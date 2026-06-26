package com.medkernel.engine.versioning;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台发布前质量门证据。
 */
public record VersionPublishQualityGate(
    boolean schemaValid,
    boolean terminologyBindingComplete,
    boolean dependencyIntegrityVerified,
    boolean safetyMonotonicityVerified,
    boolean impactSimulationPassed,
    String summary
) {
    boolean passed() {
        return schemaValid
            && terminologyBindingComplete
            && dependencyIntegrityVerified
            && safetyMonotonicityVerified
            && impactSimulationPassed;
    }

    String summaryOrDefault() {
        if (summary != null && !summary.isBlank()) {
            return summary.trim();
        }
        List<String> passedItems = new ArrayList<>();
        if (schemaValid) {
            passedItems.add("结构校验");
        }
        if (terminologyBindingComplete) {
            passedItems.add("术语字段绑定");
        }
        if (dependencyIntegrityVerified) {
            passedItems.add("依赖完整性");
        }
        if (safetyMonotonicityVerified) {
            passedItems.add("安全单调性");
        }
        if (impactSimulationPassed) {
            passedItems.add("影响评估");
        }
        return String.join("、", passedItems) + "已通过";
    }
}
