package com.medkernel.engine.security.auth;

/**
 * 登录前租户选项。
 *
 * @param tenantId 租户标识
 * @param name     登录页展示名
 * @param kind     PLATFORM 平台主租户；CUSTOMER 客户 / 集团租户
 */
public record LoginTenantOption(String tenantId, String name, String kind) {
}
