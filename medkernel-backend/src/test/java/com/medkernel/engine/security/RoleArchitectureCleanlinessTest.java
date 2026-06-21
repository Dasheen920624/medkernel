package com.medkernel.engine.security;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleArchitectureCleanlinessTest {

    private static final Set<String> LAUNCH_ROLES = Set.of(
        "platform-governance-admin",
        "platform-knowledge-governor",
        "compliance-auditor",
        "integration-operator"
    );

    private static final Set<String> COMPATIBILITY_ROLES = Set.of(
        "organization-admin",
        "identity-access-admin",
        "knowledge-governor",
        "clinical-governor",
        "clinical-decision-user",
        "nursing-collaborator",
        "medication-safety-user",
        "diagnostic-service-user",
        "quality-governor",
        "implementation-operator"
    );

    private static final Set<String> REMOVED_LEGACY_ROLES = Set.of(
        "platform-admin",
        "group-admin",
        "hospital-admin",
        "it-ops",
        "medical-affairs",
        "qa-manager",
        "insurance-manager",
        "dept-head",
        "specialist",
        "doctor",
        "nurse",
        "med-technician",
        "pharmacist",
        "audit-compliance",
        "implementation-engineer"
    );

    @Test
    void roleCatalogExposesOnlyFourLaunchResponsibilitiesAndInternalSuperadmin() {
        Set<String> actualCustomerRoles = Stream.of(RoleCode.values())
            .filter(RoleCode::customerAssignable)
            .map(RoleCode::code)
            .collect(Collectors.toSet());

        assertThat(actualCustomerRoles).containsExactlyInAnyOrderElementsOf(LAUNCH_ROLES);
        assertThat(RoleCode.fromCode("system-superadmin")).contains(RoleCode.SYSTEM_SUPERADMIN);
    }

    @Test
    void compatibilityRolesRemainReadableButCannotBeNewlyAssigned() {
        assertThat(COMPATIBILITY_ROLES)
            .allSatisfy(code -> {
                RoleCode role = RoleCode.fromCode(code).orElseThrow();
                assertThat(role.customerAssignable()).as(code).isFalse();
            });
    }

    @Test
    void removedLegacyRoleCodesAreRejectedWithoutAliases() {
        assertThat(REMOVED_LEGACY_ROLES)
            .allSatisfy(code -> assertThat(RoleCode.fromCode(code)).as(code).isEmpty());
    }
}
