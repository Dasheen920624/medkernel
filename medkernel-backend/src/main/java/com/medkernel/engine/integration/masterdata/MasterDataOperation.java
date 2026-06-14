package com.medkernel.engine.integration.masterdata;

/**
 * 院内主数据同步操作。
 *
 * <p>{@code UPSERT} 表示按来源版本新增或更新，{@code DISABLE} 表示停用来源记录而不物理删除。
 */
public enum MasterDataOperation {
    UPSERT,
    DISABLE
}
