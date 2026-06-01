package com.medkernel.engine.security.bootstrap;

/**
 * 首次部署 MFA 绑定入参；首次调用只传 label 获取 TOTP secret，确认绑定时回传 secret + code。
 */
public record BootstrapMfaRequest(String label, String secret, String code) {

    public BootstrapMfaRequest(String label) {
        this(label, null, null);
    }
}
