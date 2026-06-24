package com.medkernel.engine.pathway;

/**
 * 路径模板查询过滤条件。
 *
 * <p>按状态、病种编码、模板编码和关键词限定当前租户下的模板列表。
 */
public record PathwayTemplateFilter(
    PathwayTemplateStatus status,
    String diseaseCode,
    String templateCode,
    String keyword
) {}
