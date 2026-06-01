package com.medkernel.engine.security.bootstrap;

/**
 * MFA 绑定结果：TOTP secret 仅在 setup 阶段返回，恢复码仅在确认绑定后返回一次。
 */
public record BootstrapMfaResponse(
    boolean mfaBound,
    String secret,
    String otpauthUri,
    String recoveryCode
) {

    public BootstrapMfaResponse(boolean mfaBound, String recoveryCode) {
        this(mfaBound, null, null, recoveryCode);
    }
}
