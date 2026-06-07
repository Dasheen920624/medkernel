package com.medkernel.shared.security;

/**
 * 数据访问范围层级。
 */
public enum DataAccessLevel {
    /** 无有效数据范围权限。 */
    NONE,
    /** 仅允许访问当前科室数据。 */
    DEPARTMENT,
    /** 允许访问当前医院数据。 */
    HOSPITAL,
    /** 允许访问当前集团内跨院数据。 */
    GROUP
}
