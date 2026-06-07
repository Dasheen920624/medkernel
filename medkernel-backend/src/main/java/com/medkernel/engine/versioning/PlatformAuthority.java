package com.medkernel.engine.versioning;

import com.medkernel.shared.context.PlatformTenant;

/**
 * 平台权威资产空间约定。
 *
 * <p>平台发布版本归属唯一平台主租户，并使用独立的顶层组织路径标记平台作用域。
 * {@link InheritanceResolver} 在客户租户组织闭包无适用版本时读取平台 ACTIVE 基线；
 * 客户租户按资产身份引用，不复制平台主源。
 */
public final class PlatformAuthority {

    /** 平台资产统一归属唯一平台主租户，不另设技术租户。 */
    public static final String PLATFORM_TENANT_ID = PlatformTenant.ID;

    /** 平台基线版本的顶层组织生效域，作为继承链最一般的根。 */
    public static final String PLATFORM_ORG_PATH = "/__platform__";

    private PlatformAuthority() {
    }
}
