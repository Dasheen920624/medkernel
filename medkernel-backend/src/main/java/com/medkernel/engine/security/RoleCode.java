package com.medkernel.engine.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * MedKernel v1.0 GA · 标准角色枚举。
 *
 * <p>对应宪法 §5.2 角色矩阵的 13 个客户可分配业务角色，并额外保留系统内置超级管理员。
 *
 * <p>JWT {@code roles} claim 使用 {@link #code()}（短横线小写）；
 * Spring Security GrantedAuthority 使用 {@link #authority()}（{@code ROLE_*} 大写下划线）。
 *
 * <p>角色 → 权限的默认映射见 {@link DefaultPermissionPolicy}；
 * 医院可通过租户级角色权限覆盖表调整默认策略。
 */
public enum RoleCode {

    SYSTEM_SUPERADMIN("system-superadmin", "内置超级管理员"),
    PLATFORM_ADMIN("platform-admin", "平台管理员"),
    GROUP_ADMIN("group-admin", "集团管理员"),
    HOSPITAL_ADMIN("hospital-admin", "医院管理员"),
    IT_OPS("it-ops", "信息科"),
    MEDICAL_AFFAIRS("medical-affairs", "医务处"),
    QA_MANAGER("qa-manager", "质控办"),
    INSURANCE_MANAGER("insurance-manager", "医保办"),
    DEPT_HEAD("dept-head", "科主任"),
    SPECIALIST("specialist", "专科专家"),
    DOCTOR("doctor", "临床医生"),
    NURSE("nurse", "护理人员"),
    AUDIT_COMPLIANCE("audit-compliance", "合规审计"),
    IMPLEMENTATION_ENGINEER("implementation-engineer", "实施工程师");

    private final String code;
    private final String displayName;

    RoleCode(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /** JWT roles claim 中使用的短横线小写编码（如 {@code "doctor"}） */
    public String code() {
        return code;
    }

    /** 中文显示名 */
    public String displayName() {
        return displayName;
    }

    /** Spring Security 权威字符串（{@code ROLE_DOCTOR}） */
    public String authority() {
        return "ROLE_" + name();
    }

    /** 是否为系统强制内置、不可通过租户用户管理手工分配或降权的超级管理员角色。 */
    public boolean systemSuperAdmin() {
        return this == SYSTEM_SUPERADMIN;
    }

    /** 是否属于 13 个客户可分配业务角色矩阵。 */
    public boolean customerAssignable() {
        return !systemSuperAdmin();
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

    /** 从 Spring Security authority 字符串（{@code ROLE_DOCTOR} / {@code DOCTOR}）反查 */
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
