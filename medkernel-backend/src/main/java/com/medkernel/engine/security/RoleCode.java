package com.medkernel.engine.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * MedKernel 标准职责角色枚举。
 *
 * <p>角色只表达系统责任，不重复表达机构层级、科室或人员专业岗位。
 * 集团、医院、分院等差异由组织范围承载，医师、护士、药师等由人员任职承载。
 * 项目上线前不识别任何早期固定岗位角色编码。
 *
 * <p>JWT {@code roles} claim 使用 {@link #code()}（短横线小写）；
 * Spring Security GrantedAuthority 使用 {@link #authority()}（{@code ROLE_*} 大写下划线）。
 *
 * <p>角色 → 权限的默认映射见 {@link DefaultPermissionPolicy}；
 * 医院可通过租户级角色权限覆盖表调整默认策略。
 */
public enum RoleCode {

    SYSTEM_SUPERADMIN("system-superadmin", "内置超级管理员"),
    PLATFORM_GOVERNANCE_ADMIN("platform-governance-admin", "平台管理员"),
    PLATFORM_KNOWLEDGE_GOVERNOR("platform-knowledge-governor", "知识运营员"),
    ORGANIZATION_ADMIN("organization-admin", "机构管理员"),
    IDENTITY_ACCESS_ADMIN("identity-access-admin", "人员与访问管理员"),
    KNOWLEDGE_GOVERNOR("knowledge-governor", "机构知识治理员"),
    CLINICAL_GOVERNOR("clinical-governor", "临床治理负责人"),
    CLINICAL_DECISION_USER("clinical-decision-user", "临床决策使用者"),
    NURSING_COLLABORATOR("nursing-collaborator", "护理协同人员"),
    MEDICATION_SAFETY_USER("medication-safety-user", "药事安全人员"),
    DIAGNOSTIC_SERVICE_USER("diagnostic-service-user", "医技协同人员"),
    QUALITY_GOVERNOR("quality-governor", "质量与医保治理员"),
    COMPLIANCE_AUDITOR("compliance-auditor", "审计查看员"),
    INTEGRATION_OPERATOR("integration-operator", "接入运维员"),
    IMPLEMENTATION_OPERATOR("implementation-operator", "实施运维员");

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

    /** Spring Security 权威字符串（例如 {@code ROLE_CLINICAL_DECISION_USER}）。 */
    public String authority() {
        return "ROLE_" + name();
    }

    /** 是否为系统强制内置、不可通过租户用户管理手工分配或降权的超级管理员角色。 */
    public boolean systemSuperAdmin() {
        return this == SYSTEM_SUPERADMIN;
    }

    /**
     * 是否属于首发客户可分配职责。
     *
     * <p>其他角色编码继续用于历史令牌、审计与隐藏应用面兼容，但不再允许新账号分配。
     */
    public boolean customerAssignable() {
        return switch (this) {
            case PLATFORM_GOVERNANCE_ADMIN,
                 PLATFORM_KNOWLEDGE_GOVERNOR,
                 COMPLIANCE_AUDITOR,
                 INTEGRATION_OPERATOR -> true;
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
     * （例如 {@code ROLE_CLINICAL_DECISION_USER} / {@code CLINICAL_DECISION_USER}）反查。
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
