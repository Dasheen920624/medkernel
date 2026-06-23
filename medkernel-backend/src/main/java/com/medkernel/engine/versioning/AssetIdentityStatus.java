package com.medkernel.engine.versioning;

/**
 * 稳定资产身份状态。
 */
public enum AssetIdentityStatus {
    /** 可维护并创建下一内容版本。 */
    ACTIVE,
    /** 停止创建新版本，但永久保留历史版本与运行证据。 */
    RETIRED
}
