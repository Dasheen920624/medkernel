package com.medkernel.engine.context;

/**
 * 某类标准临床对象中一个可映射编码字段的定义。
 */
public record ClinicalCodeMappingAnchorDefinition(
    CanonicalResourceType resourceType,
    String fieldName,
    String targetDictionaryKey
) {}
