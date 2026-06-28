package com.medkernel.engine.security.auth;

import java.util.List;

/**
 * 院方统一身份委托登录状态；身份来源待配置时必须诚实返回 NOT_CONNECTED。
 */
public record DelegatedAuthStatusResponse(
    String mode,
    boolean enabled,
    String status,
    List<String> providers,
    String message
) {
}
