package com.medkernel.engine.pkg;

/**
 * 灰度发布作用范围类型。
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
    DEPARTMENT,
    /** 指定专病 / 专科维度 */
    SPECIALTY
}
