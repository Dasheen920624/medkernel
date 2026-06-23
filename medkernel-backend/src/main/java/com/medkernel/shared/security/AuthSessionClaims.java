package com.medkernel.shared.security;

/**
 * 平台无状态会话 JWT claim 名称。
 */
public final class AuthSessionClaims {

    public static final String SESSION_STARTED_AT = "session_started_at";
    public static final String MFA_VERIFIED = "mfa_verified";

    private AuthSessionClaims() {
    }
}
