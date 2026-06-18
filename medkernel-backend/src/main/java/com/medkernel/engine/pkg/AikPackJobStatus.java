package com.medkernel.engine.pkg;

/**
 * AIK 知识包装配作业状态。
 */
public enum AikPackJobStatus {
    /** 已完成装配并登记配置包草稿 */
    PACKAGED,
    /** 装配失败，未形成可发布知识包 */
    FAILED
}
