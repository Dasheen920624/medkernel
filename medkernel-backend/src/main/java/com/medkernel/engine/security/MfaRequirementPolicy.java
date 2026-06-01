package com.medkernel.engine.security;

import java.util.Collection;
import java.util.Set;

/**
 * 需要强制 MFA 的高风险平台角色策略。
 */
public final class MfaRequirementPolicy {

    private static final Set<String> HIGH_RISK_ROLES = Set.of(
        RoleCode.PLATFORM_ADMIN.code(),
        RoleCode.GROUP_ADMIN.code(),
        RoleCode.HOSPITAL_ADMIN.code(),
        RoleCode.IT_OPS.code()
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
