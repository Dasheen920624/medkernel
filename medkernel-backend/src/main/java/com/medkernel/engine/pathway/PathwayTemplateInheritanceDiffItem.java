package com.medkernel.engine.pathway;

/**
 * 路径模板继承差异项。
 *
 * <p>用于表达下级模板相对父级有效模板的新增、覆盖和禁用事实。
 */
public record PathwayTemplateInheritanceDiffItem(
    String itemType,
    String itemCode,
    PathwayInheritanceChangeType changeType,
    String fieldName,
    String parentValue,
    String childValue
) {}
