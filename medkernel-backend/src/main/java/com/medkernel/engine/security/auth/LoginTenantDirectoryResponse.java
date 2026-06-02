package com.medkernel.engine.security.auth;

import java.util.List;

/**
 * 登录前租户字典。
 *
 * @param primaryTenants     登录页第一层租户；有客户 / 集团租户时优先返回客户租户
 * @param platformTenant     唯一平台主租户，客户租户存在时退到第二层
 * @param hasCustomerTenants 是否已经开通客户 / 集团租户
 */
public record LoginTenantDirectoryResponse(
    List<LoginTenantOption> primaryTenants,
    LoginTenantOption platformTenant,
    boolean hasCustomerTenants
) {
}
