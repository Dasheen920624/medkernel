package com.medkernel.engine.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * MedKernel 标准职责角色枚举。
 *
 * <p>角色只表达系统责任，不重复表达机构层级、科室或人员专业岗位。
 * 集团、医院、分院等差异由组织范围承载，医师、护士、药师等由人员任职承载。
 * 上线职责固定为平台管理、医疗引擎运营、临床使用和审计四类。
 * 旧职责编码不再参与登录、授权或新账号分配。
 *
 * <p>JWT {@code roles} claim 使用 {@link #code()}（短横线小写）；
 * Spring Security GrantedAuthority 使用 {@link #authority()}（{@code ROLE_*} 大写下划线）。
 *
 * <p>角色 → 权限的固定映射见 {@link DefaultPermissionPolicy}。
 */
public enum RoleCode {

    SYSTEM_SUPERADMIN("system-superadmin", "内置超级管理员"),
    PLATFORM_ADMIN("platform-admin", "平台管理员"),
    ENGINE_OPERATOR("engine-operator", "医疗引擎运营员"),
    CLINICAL_USER("clinical-user", "临床使用者"),
    AUDITOR("auditor", "审计员");

    private final String code;
    private final String displayName;

    RoleCode(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /** JWT roles claim 中使用的短横线小写编码。 */
    public String code() {
        return code;
    }

    /** 中文显示名 */
    public String displayName() {
        return displayName;
    }

    /** Spring Security 权威字符串（例如 {@code ROLE_CLINICAL_USER}）。 */
    public String authority() {
        return "ROLE_" + name();
    }

    /** 是否为系统强制内置、不可通过租户用户管理手工分配或降权的超级管理员角色。 */
    public boolean systemSuperAdmin() {
        return this == SYSTEM_SUPERADMIN;
    }

    /**
     * 是否属于客户可分配职责。
     */
    public boolean customerAssignable() {
        return switch (this) {
            case PLATFORM_ADMIN,
                 ENGINE_OPERATOR,
                 CLINICAL_USER,
                 AUDITOR -> true;
            default -> false;
        };
    }

    public static Optional<RoleCode> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim();
        return Arrays.stream(values())
            .filter(r -> r.code.equalsIgnoreCase(normalized))
            .findFirst();
    }

    /**
     * 从 Spring Security authority 字符串
     * （例如 {@code ROLE_CLINICAL_USER} / {@code CLINICAL_USER}）反查。
     */
    public static Optional<RoleCode> fromAuthority(String authority) {
        if (authority == null) {
            return Optional.empty();
        }
        String normalized = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
        return Arrays.stream(values())
            .filter(r -> r.name().equalsIgnoreCase(normalized))
            .findFirst();
    }
}
