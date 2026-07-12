package com.medkernel.engine.knowledge.authority;

/** 外置签名密钥的公开生命周期状态。 */
public enum SigningKeyStatus {
    STANDBY,
    ACTIVE,
    DISABLED,
    REVOKED
}
