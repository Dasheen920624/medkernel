package com.medkernel.engine.pathway;

import java.util.List;

/**
 * 临床路径图。
 *
 * <p>由节点集合和边集合组成，供试运行和真实推进时按同一规则计算下一节点。
 */
public record PathwayGraph(
    List<PathwayNode> nodes,
    List<PathwayEdge> edges
) {

    /**
     * 创建不可变路径图快照，并将空集合归一为空列表。
     */
    public PathwayGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
