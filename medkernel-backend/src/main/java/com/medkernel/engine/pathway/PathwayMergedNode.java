package com.medkernel.engine.pathway;

/**
 * 路径模板继承合并后的节点投影。
 *
 * <p>字段与可执行节点保持一致，并额外标明节点来自父级、覆盖还是新增。
 */
public record PathwayMergedNode(
    String nodeCode,
    String name,
    PathwayNodeType nodeType,
    String milestoneCode,
    Integer sortOrder,
    String responsibleRole,
    String accountableRole,
    String consultedRolesJson,
    String informedRolesJson,
    String dependencyJson,
    Integer timeWindowMinutes,
    Boolean terminalFlag,
    String configJson,
    PathwayInheritanceOrigin origin
) {
    public static PathwayMergedNode from(PathwayNode node, PathwayInheritanceOrigin origin) {
        return new PathwayMergedNode(
            node.nodeCode(),
            node.name(),
            node.nodeType(),
            node.milestoneCode(),
            node.sortOrder(),
            node.responsibleRole(),
            node.accountableRole(),
            node.consultedRolesJson(),
            node.informedRolesJson(),
            node.dependencyJson(),
            node.timeWindowMinutes(),
            node.terminalFlag(),
            node.configJson(),
            origin
        );
    }

    public PathwayMergedNode withOrigin(PathwayInheritanceOrigin nextOrigin) {
        return new PathwayMergedNode(
            nodeCode, name, nodeType, milestoneCode, sortOrder, responsibleRole, accountableRole,
            consultedRolesJson, informedRolesJson, dependencyJson, timeWindowMinutes, terminalFlag,
            configJson, nextOrigin
        );
    }
}
