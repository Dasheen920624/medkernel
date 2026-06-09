package com.medkernel.engine.integration.runtime;

/**
 * 第三方包分发对账状态。
 */
public enum ThirdPartyReconciliationStatus {
    NOT_DISTRIBUTED,
    IN_PROGRESS,
    SUCCESS,
    NOT_SYNCED,
    FAILED
}
