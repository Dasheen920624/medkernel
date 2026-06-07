package com.medkernel.engine.pathway;

import java.util.List;

/**
 * 路径模板继承差异与合并结果。
 */
public record PathwayTemplateInheritanceDiffResponse(
    String templateId,
    String parentTemplateId,
    List<PathwayTemplateInheritanceDiffItem> diffItems,
    List<PathwayMergedNode> mergedNodes,
    List<PathwayEdge> mergedEdges,
    String traceId
) {
    public PathwayTemplateInheritanceDiffResponse {
        diffItems = diffItems == null ? List.of() : List.copyOf(diffItems);
        mergedNodes = mergedNodes == null ? List.of() : List.copyOf(mergedNodes);
        mergedEdges = mergedEdges == null ? List.of() : List.copyOf(mergedEdges);
    }
}
