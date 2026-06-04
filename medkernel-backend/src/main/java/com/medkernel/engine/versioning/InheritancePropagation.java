package com.medkernel.engine.versioning;

/**
 * 组织继承覆盖的传播范围。
 */
public enum InheritancePropagation {
    /** 复用：覆盖对本节点及其所有下级生效，直至某下级进一步覆盖。 */
    INHERITABLE,
    /** 独有：覆盖仅本节点生效，下级回退到上一层适用版本。 */
    EXCLUSIVE
}
