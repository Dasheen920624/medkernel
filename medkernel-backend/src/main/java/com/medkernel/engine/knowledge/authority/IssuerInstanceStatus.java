package com.medkernel.engine.knowledge.authority;

/** 发布实例生命周期；只有 {@link #ACTIVE} 实例可以签发新平台包。 */
public enum IssuerInstanceStatus {
    STANDBY,
    ACTIVE,
    FROZEN,
    HANDED_OVER,
    REVOKED
}
