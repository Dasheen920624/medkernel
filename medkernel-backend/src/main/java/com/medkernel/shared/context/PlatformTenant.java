package com.medkernel.shared.context;

/**
 * 平台主租户常量。
 *
 * <p>{@link #ID} 是唯一内置的平台数据租户，承载全局医疗知识、标准包和初始超级管理员。
 * 客户集团、医院、院区等租户必须由平台租户分配或开通，不能复用本 ID。
 * 客户租户的知识只能来自平台主源同步 / 离线下发副本，随后在本租户内覆盖或新增，不能反写平台主源。
 *
 * <p>{@code SYSTEM} 只作为系统配置、角色目录等技术命名空间，不是可登录、可承载业务知识的数据租户。
 */
public final class PlatformTenant {

    /** 唯一内置平台主租户 ID。 */
    public static final String ID = "t-1";

    /** 客户面推荐显示名。 */
    public static final String DISPLAY_NAME = "平台治理入口（唯一内置）";

    /** 角色与数据范围等客户页面使用的简洁名称。 */
    public static final String SCOPE_DISPLAY_NAME = "平台治理入口";

    /** 系统配置 / 角色目录命名空间，不等同 {@link #ID}。 */
    public static final String SYSTEM_NAMESPACE = "SYSTEM";

    private PlatformTenant() {
    }

    public static String tenantOrPlatform(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return ID;
        }
        return tenantId.trim();
    }

    public static boolean isPlatformTenant(String tenantId) {
        return ID.equals(tenantOrPlatform(tenantId));
    }
}
