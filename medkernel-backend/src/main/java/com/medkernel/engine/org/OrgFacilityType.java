package com.medkernel.engine.org;

/**
 * FACILITY 组织节点的机构类型。
 *
 * <p>组织层级只表达「机构」这一层，具体是医院、社区卫生服务中心、卫生院或站点由本枚举区分。
 */
public enum OrgFacilityType {
    /** 医院。 */
    HOSPITAL,
    /** 社区卫生服务中心。 */
    COMMUNITY_HEALTH_CENTER,
    /** 乡镇卫生院。 */
    TOWNSHIP_CLINIC,
    /** 卫生服务站 / 延伸站点。 */
    STATION,
    /** 其他医疗机构。 */
    OTHER
}
