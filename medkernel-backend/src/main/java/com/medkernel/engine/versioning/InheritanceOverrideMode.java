package com.medkernel.engine.versioning;

/**
 * 组织继承覆盖方式。
 */
public enum InheritanceOverrideMode {
    /** 下级组织使用本地版本替换继承版本。 */
    REPLACE,
    /** 下级组织尝试关闭继承版本。 */
    DISABLE,
    /** 本组织新增平台无基线的独有版本，可按传播范围复用或仅本级生效。 */
    ADD
}
