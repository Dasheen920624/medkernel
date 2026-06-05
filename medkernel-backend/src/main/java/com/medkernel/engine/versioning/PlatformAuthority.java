package com.medkernel.engine.versioning;

/**
 * 平台权威层空间约定（设计附录 G·决策 D1：平台层 = {@code __platform__} 租户 + 顶层组织路径，迁移最小）。
 *
 * <p>平台发布的资产版本以 {@link #PLATFORM_TENANT_ID} 为租户、{@link #PLATFORM_ORG_PATH} 为组织生效域持久化，
 * 与任何真实租户隔离；{@link InheritanceResolver} 在租户组织闭包无适用版本时，按此约定前置回退读取平台
 * ACTIVE 基线（{@link SourceTier#PLATFORM}）。租户对平台资产按 {@code asset_identity} 引用、不预先复制副本。
 */
public final class PlatformAuthority {

    /** 平台权威层租户标识：平台版本归属此保留租户，高于所有真实租户。 */
    public static final String PLATFORM_TENANT_ID = "__platform__";

    /** 平台权威层顶层组织路径：平台基线版本的组织生效域，作为继承链最一般的根。 */
    public static final String PLATFORM_ORG_PATH = "/__platform__";

    private PlatformAuthority() {
    }
}
