package com.medkernel.engine.org;

/**
 * 组织目录的客户任务范围。
 */
public enum OrgDirectoryScope {
    /**
     * 可承载人员任职的服务机构层级：服务机构根、区域、医疗服务机构和院区。
     */
    SERVICE_ORGANIZATION,

    /**
     * 可承载业务配置的全部机构内组织层级，不包含平台治理层。
     */
    BUSINESS_SCOPE
}
