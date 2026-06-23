package com.medkernel.engine.pathway;

import java.util.List;

/**
 * 指定医院运行修订与触发点下的候选路径集合。
 */
public record RuntimePathwaySelection(
    String runtimeReleaseId,
    String platformBaselineReleaseId,
    List<RuntimePathwayReference> pathways
) {
    public RuntimePathwaySelection {
        pathways = pathways == null ? List.of() : List.copyOf(pathways);
    }
}
