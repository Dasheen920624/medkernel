package com.medkernel.engine.integration.masterdata;

/**
 * 可由院内业务系统同步的主数据资源类型。
 *
 * <p>当前覆盖组织机构、院内人员和本地术语字典。
 */
public enum MasterDataResourceType {
    ORG_UNIT,
    PERSON,
    LOCAL_TERM
}
