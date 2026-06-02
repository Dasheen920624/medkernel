package com.medkernel.engine.versioning;

/**
 * 组织继承覆盖方式。
 */
public enum InheritanceOverrideMode {
    /** 下级组织使用本地版本替换继承版本。 */
    REPLACE,
    /** 下级组织尝试关闭继承版本。 */
    DISABLE
}
