package com.medkernel.engine.security;

import java.util.Collection;
import java.util.Set;

/**
 * 需要强制 MFA 的高风险平台角色策略。
 */
public final class MfaRequirementPolicy {

    private static final Set<String> HIGH_RISK_ROLES = Set.of(
        RoleCode.SYSTEM_SUPERADMIN.code(),
        RoleCode.PLATFORM_GOVERNANCE_ADMIN.code(),
        RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR.code(),
        RoleCode.ORGANIZATION_ADMIN.code(),
        RoleCode.IDENTITY_ACCESS_ADMIN.code(),
        RoleCode.KNOWLEDGE_GOVERNOR.code(),
        RoleCode.CLINICAL_GOVERNOR.code(),
        RoleCode.QUALITY_GOVERNOR.code(),
        RoleCode.COMPLIANCE_AUDITOR.code(),
        RoleCode.INTEGRATION_OPERATOR.code(),
        RoleCode.IMPLEMENTATION_OPERATOR.code()
    );

    private MfaRequirementPolicy() {
    }

    public static boolean requiresMfa(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleCodes.stream().anyMatch(HIGH_RISK_ROLES::contains);
    }
}
