package com.medkernel.engine.integration.masterdata;

/**
 * 院内主数据同步模式。
 *
 * <p>增量模式只处理本批变更；全量快照模式还会停用本次快照中缺失的既有来源记录。
 */
public enum MasterDataSyncMode {
    INCREMENTAL,
    FULL_SNAPSHOT
}
