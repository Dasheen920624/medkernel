package com.medkernel.engine.security.auth;

import java.util.List;

/**
 * 平台账号登录出参：用户标识、租户、角色编码列表、首登改密与 MFA 状态。
 */
public record LoginResponse(
    String userId,
    String tenantId,
    List<String> roles,
    boolean mustChangePwd,
    boolean mfaRequired,
    boolean mfaBound
) {}
