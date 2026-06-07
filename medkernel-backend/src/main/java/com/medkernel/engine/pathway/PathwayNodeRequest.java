package com.medkernel.engine.pathway;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建路径节点的请求片段。
 *
 * <p>用于在创建模板时定义临床步骤、节点类型、所属里程碑、排序、责任角色、依赖、时间窗和节点配置。
 */
public record PathwayNodeRequest(
    @NotBlank String nodeCode,
    @NotBlank String name,
    @NotNull PathwayNodeType nodeType,
    String milestoneCode,
    @Min(0) Integer sortOrder,
    String responsibleRole,
    JsonNode dependency,
    @Min(0) Integer timeWindowMinutes,
    Boolean terminal,
    JsonNode config
) {
    public PathwayNodeRequest(String nodeCode,
                              String name,
                              PathwayNodeType nodeType,
                              Integer sortOrder,
                              String responsibleRole,
                              JsonNode dependency,
                              Integer timeWindowMinutes,
                              Boolean terminal,
                              JsonNode config) {
        this(nodeCode, name, nodeType, null, sortOrder, responsibleRole, dependency,
            timeWindowMinutes, terminal, config);
    }
}
