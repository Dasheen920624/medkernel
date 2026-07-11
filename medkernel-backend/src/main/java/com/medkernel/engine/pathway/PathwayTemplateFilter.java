package com.medkernel.engine.pathway;

/**
 * 临床路径查询过滤条件。
 *
 * <p>按状态、病种编码、路径编码和关键词限定当前租户下的临床路径列表。
 */
public record PathwayTemplateFilter(
    PathwayTemplateStatus status,
    String diseaseCode,
    String templateCode,
    String keyword
) {}
