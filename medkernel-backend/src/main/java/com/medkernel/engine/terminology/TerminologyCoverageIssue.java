package com.medkernel.engine.terminology;

/**
 * 规则/路径发布前发现的标准编码对照覆盖问题。
 *
 * @param fieldPath        条件引用的上下文字段路径
 * @param codeSystem       字段绑定的标准编码系统
 * @param code             条件引用的标准编码
 * @param status           覆盖状态
 * @param mappedLocalCount 已确认院内编码对照数量
 */
public record TerminologyCoverageIssue(
    String fieldPath,
    String codeSystem,
    String code,
    String status,
    int mappedLocalCount
) {}
