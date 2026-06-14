package com.medkernel.engine.security;

/**
 * MedKernel v1.0 GA · 五维权限维度。
 *
 * <p>所有权限点必须落入菜单、动作、数据、资产、环境之一，供统一 RBAC 引擎、
 * 数据范围判断和前端菜单可见性共享同一套口径。
 */
public enum PermissionDimension {
    /** 菜单可见性权限，控制现行业务域内普通功能入口的显隐。 */
    MENU,
    /** 业务动作权限，控制读写、审核、发布、导出等操作。 */
    ACTION,
    /** 数据范围权限，控制本科室、全院、集团跨院和脱敏访问。 */
    DATA,
    /** 资产范围权限，控制知识包、字典、规则、路径和配置包等资产。 */
    ASSET,
    /** 环境范围权限，控制测试、试运行、正式和应急环境。 */
    ENVIRONMENT
}
