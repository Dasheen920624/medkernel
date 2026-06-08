package com.medkernel.engine.rule;

/**
 * 条件片段引用的稳定键。
 *
 * @param fragmentCode   片段编码
 * @param version        片段版本号
 * @param packageVersion 片段所属知识包版本
 */
public record ConditionFragmentReference(
    String fragmentCode,
    int version,
    String packageVersion
) {}
