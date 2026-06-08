package com.medkernel.engine.versioning;

/**
 * 版本发布作用域类型。
 *
 * <p>该枚举仅表达组织作用域；专病等横切维度写入 applicableScope，放量方式由 {@link RolloutStrategy} 表达。
 */
public enum VersionReleaseScopeType {
    ALL,
    REGION,
    FACILITY,
    CAMPUS,
    DEPARTMENT,
    WARD
}
