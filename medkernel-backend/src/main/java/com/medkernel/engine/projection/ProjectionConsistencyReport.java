package com.medkernel.engine.projection;

import java.util.List;

/**
 * 关系库权威源与投影快照的一致性报告。
 */
public record ProjectionConsistencyReport(
    ProjectionTargetType targetType,
    String tenantId,
    boolean consistent,
    int sourceCount,
    int projectionCount,
    String sourceHash,
    String projectionHash,
    List<ProjectionDiffItem> missing,
    List<ProjectionDiffItem> extra,
    List<ProjectionDiffItem> changed
) {
    public ProjectionConsistencyReport {
        missing = missing == null ? List.of() : List.copyOf(missing);
        extra = extra == null ? List.of() : List.copyOf(extra);
        changed = changed == null ? List.of() : List.copyOf(changed);
    }
}
