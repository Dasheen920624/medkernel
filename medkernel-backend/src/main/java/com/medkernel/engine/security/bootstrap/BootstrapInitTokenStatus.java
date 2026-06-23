package com.medkernel.engine.security.bootstrap;

/**
 * 首次部署引导 init token 生命周期：有效、已使用、已撤销。
 */
public enum BootstrapInitTokenStatus {
    ACTIVE,
    USED,
    REVOKED
}
