package com.medkernel.engine.pathway;

/**
 * 路径模板合并后节点来源。
 */
public enum PathwayInheritanceOrigin {
    /**
     * 来自父级模板且未被下级覆盖。
     */
    INHERITED,

    /**
     * 下级模板覆盖了父级同编码节点。
     */
    OVERRIDDEN,

    /**
     * 下级模板新增节点。
     */
    ADDED
}
