package com.medkernel.engine.security.bootstrap;

import java.util.List;

/**
 * 首发平台管理员创建结果；不返回口令或口令摘要。
 */
public record BootstrapPasswordResponse(
    String userId,
    String tenantId,
    String username,
    List<String> roles,
    boolean mustChangePwd
) {}
