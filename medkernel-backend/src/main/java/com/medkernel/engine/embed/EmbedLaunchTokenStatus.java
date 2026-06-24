package com.medkernel.engine.embed;

/**
 * 嵌入启动凭证生命周期状态。
 */
public enum EmbedLaunchTokenStatus {
    UNUSED,
    USED,
    EXPIRED,
    REVOKED
}
