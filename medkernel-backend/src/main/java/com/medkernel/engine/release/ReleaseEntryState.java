package com.medkernel.engine.release;

/**
 * 不可变发布清单中的资产启停状态。
 */
public enum ReleaseEntryState {
    /** 锁定并运行一个精确正式版本。 */
    ACTIVE,
    /** 明确停用稳定资产身份，防止后续基线升级静默恢复。 */
    DISABLED
}
