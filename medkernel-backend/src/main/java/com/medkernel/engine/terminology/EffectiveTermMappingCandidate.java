package com.medkernel.engine.terminology;

/**
 * 数据库查询得到的已激活术语映射候选，携带发布作用域供解析器执行最近层级优先。
 */
public record EffectiveTermMappingCandidate(
    Long mappingId,
    Long standardTermId,
    String standardCode,
    String versionNo,
    String scopeLevel
) {
}
