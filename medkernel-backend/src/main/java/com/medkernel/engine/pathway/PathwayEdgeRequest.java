package com.medkernel.engine.pathway;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建路径边的请求片段。
 *
 * <p>用于在创建临床路径时声明节点之间的可达关系、分支类型、条件摘要和推进优先级。
 */
public record PathwayEdgeRequest(
    @NotBlank String edgeCode,
    @NotBlank String fromNodeCode,
    @NotBlank String toNodeCode,
    @NotNull PathwayEdgeType edgeType,
    JsonNode condition,
    @Min(0) Integer priority
) {}
