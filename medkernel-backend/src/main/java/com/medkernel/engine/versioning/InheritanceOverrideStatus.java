package com.medkernel.engine.versioning;

/**
 * 继承覆盖运行事实状态。
 */
public enum InheritanceOverrideStatus {
    /** 已启用，可参与解析。 */
    ACTIVE,
    /** 已退役，仅用于历史重放窗口。 */
    RETIRED
}
