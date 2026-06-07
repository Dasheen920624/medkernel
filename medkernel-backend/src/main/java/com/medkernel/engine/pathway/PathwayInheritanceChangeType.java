package com.medkernel.engine.pathway;

/**
 * 路径模板继承差异类型。
 */
public enum PathwayInheritanceChangeType {
    /**
     * 下级模板覆盖父级字段。
     */
    OVERRIDDEN,

    /**
     * 下级模板新增节点或边。
     */
    ADDED,

    /**
     * 下级模板禁用父级节点。
     */
    DISABLED
}
