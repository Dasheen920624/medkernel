package com.medkernel.engine.pkg;

/**
 * 发布组织作用范围类型。
 *
 * <p>专病等横切维度由请求上下文的 specialtyId 表达，不混入组织层级。
 */
public enum ReleaseScopeType {
    /** 全体 */
    ALL,
    /** 指定集团 */
    GROUP,
    /** 指定医院 */
    HOSPITAL,
    /** 指定院区 */
    CAMPUS,
    /** 指定社区服务点 */
    SITE,
    /** 指定科室 */
    DEPARTMENT
}
