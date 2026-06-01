package com.medkernel.engine.security.auth;

import java.util.List;

/**
 * 院方统一身份委托登录状态；未接入真实 IdP 时必须诚实返回 NOT_CONNECTED。
 */
public record DelegatedAuthStatusResponse(
    String mode,
    boolean enabled,
    String status,
    List<String> providers,
    String message
) {
}
