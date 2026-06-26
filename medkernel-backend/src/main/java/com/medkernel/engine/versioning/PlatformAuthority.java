package com.medkernel.engine.versioning;

import com.medkernel.shared.context.PlatformTenant;

/**
 * 平台权威资产范围约定。
 *
 * <p>平台标准版本归属唯一平台主机构，并使用独立的顶层组织路径标记平台作用域。
 * {@link InheritanceResolver} 在客户机构组织闭包无适用版本时读取平台生效版本；
 * 客户机构按资产身份引用，不复制平台主源。
 */
public final class PlatformAuthority {

    /** 平台资产统一归属唯一平台主机构，不另设技术机构。 */
    public static final String PLATFORM_TENANT_ID = PlatformTenant.ID;

    /** 平台标准版本的顶层组织生效域，作为继承链最一般的根。 */
    public static final String PLATFORM_ORG_PATH = "/__platform__";

    private PlatformAuthority() {
    }
}
