package com.medkernel.engine.pathway;

import java.util.List;

import com.medkernel.engine.versioning.AssetVersionStatus;

/**
 * 路径模板详情响应。
 *
 * <p>聚合模板主数据、阶段里程碑、节点、边、指标绑定和 traceId，用于模板查看、发布校验和试运行准备。
 */
public record PathwayTemplateDetailResponse(
    PathwayTemplate template,
    List<PathwayMilestone> milestones,
    List<PathwayNode> nodes,
    List<PathwayEdge> edges,
    List<SpecialtyMetricBinding> metricBindings,
    List<PathwayOutcomeBinding> outcomeBindings,
    AssetVersionStatus deploymentStatus,
    String traceId
) {

    /**
     * 创建不可变详情响应，并将空里程碑、节点、边和指标绑定归一为空列表。
     */
    public PathwayTemplateDetailResponse {
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        metricBindings = metricBindings == null ? List.of() : List.copyOf(metricBindings);
        outcomeBindings = outcomeBindings == null ? List.of() : List.copyOf(outcomeBindings);
    }

    public PathwayTemplateDetailResponse(
            PathwayTemplate template,
            List<PathwayMilestone> milestones,
            List<PathwayNode> nodes,
            List<PathwayEdge> edges,
            List<SpecialtyMetricBinding> metricBindings,
            AssetVersionStatus deploymentStatus,
            String traceId) {
        this(template, milestones, nodes, edges, metricBindings, List.of(), deploymentStatus, traceId);
    }
}
