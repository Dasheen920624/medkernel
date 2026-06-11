package com.medkernel.engine.security;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPermissionPolicyTest {

    @Test
    void platformGovernanceAdminHasEveryNonEmergencyPermission() {
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);
        expected.remove(PermissionCode.ENV_EMERGENCY);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_GOVERNANCE_ADMIN))
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void systemSuperAdminAloneIncludesEmergencyPermission() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.SYSTEM_SUPERADMIN))
            .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(PermissionCode.class));
        for (RoleCode role : RoleCode.values()) {
            if (role != RoleCode.SYSTEM_SUPERADMIN) {
                assertThat(DefaultPermissionPolicy.permissionsOf(role))
                    .as("%s 不得获得系统紧急权限", role.code())
                    .doesNotContain(PermissionCode.ENV_EMERGENCY);
            }
        }
    }

    @Test
    void organizationAdminGovernsInstitutionWithoutPlatformOrSystemAuthority() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.ORGANIZATION_ADMIN))
            .contains(
                PermissionCode.ORG_WRITE,
                PermissionCode.TENANT_OVERRIDE,
                PermissionCode.PACKAGE_PUBLISH,
                PermissionCode.PACKAGE_ROLLBACK,
                PermissionCode.MENU_ADMIN_USERS,
                PermissionCode.MENU_IDENTITY_BINDINGS)
            .doesNotContain(
                PermissionCode.PLATFORM_PUBLISH,
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.ENV_EMERGENCY);
    }

    @Test
    void platformKnowledgeGovernorOwnsPlatformKnowledgePublishingWithoutSystemAdministration() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR))
            .contains(
                PermissionCode.PLATFORM_PUBLISH,
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.KNOWLEDGE_WITHDRAW,
                PermissionCode.TERM_PUBLISH,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.PATHWAY_PUBLISH,
                PermissionCode.MENU_KNOWLEDGE_GOVERNANCE)
            .doesNotContain(
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.MENU_ADMIN_USERS,
                PermissionCode.ENV_EMERGENCY);
    }

    @Test
    void identityAccessAdminHasPersonnelAndAccessAuthorityWithoutClinicalOrKnowledgeAssets() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IDENTITY_ACCESS_ADMIN))
            .contains(
                PermissionCode.ORG_READ,
                PermissionCode.ORG_WRITE,
                PermissionCode.TENANT_READ,
                PermissionCode.AUDIT_READ,
                PermissionCode.MENU_ADMIN_USERS,
                PermissionCode.MENU_IDENTITY_BINDINGS)
            .doesNotContain(
                PermissionCode.KNOWLEDGE_WRITE,
                PermissionCode.RULE_WRITE,
                PermissionCode.PATHWAY_WRITE,
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.PLATFORM_PUBLISH);
    }

    @Test
    void institutionKnowledgeGovernorPublishesInstitutionAssetsButCannotPublishPlatformBaseline() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.KNOWLEDGE_GOVERNOR))
            .contains(
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.KNOWLEDGE_WITHDRAW,
                PermissionCode.TERM_PUBLISH,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.PATHWAY_PUBLISH,
                PermissionCode.PROJECTION_READ)
            .doesNotContain(
                PermissionCode.PLATFORM_PUBLISH,
                PermissionCode.RULE_OVERRIDE,
                PermissionCode.SYSTEM_MANAGE);
    }

    @Test
    void clinicalGovernorOwnsClinicalReviewAndRuleOverrideWithoutKnowledgePublication() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.CLINICAL_GOVERNOR))
            .contains(
                PermissionCode.KNOWLEDGE_REVIEW,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.RULE_OVERRIDE,
                PermissionCode.PATHWAY_PUBLISH,
                PermissionCode.EVALUATION_REMEDIATE)
            .doesNotContain(
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.KNOWLEDGE_WITHDRAW,
                PermissionCode.TERM_PUBLISH,
                PermissionCode.SYSTEM_MANAGE);
    }

    @Test
    void clinicalDecisionUserCanAcceptRecommendationAndRecordManualOverrideOnly() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.CLINICAL_DECISION_USER))
            .contains(
                PermissionCode.RECOMMENDATION_READ,
                PermissionCode.RECOMMENDATION_ACCEPT,
                PermissionCode.RULE_READ,
                PermissionCode.RULE_OVERRIDE,
                PermissionCode.PATHWAY_READ)
            .doesNotContain(
                PermissionCode.RULE_WRITE,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.SYSTEM_MANAGE);
    }

    @Test
    void nursingMedicationAndDiagnosticResponsibilitiesStayLeastPrivilege() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.NURSING_COLLABORATOR))
            .contains(PermissionCode.RECOMMENDATION_ACCEPT, PermissionCode.FOLLOWUP_WRITE)
            .doesNotContain(PermissionCode.RULE_OVERRIDE, PermissionCode.KNOWLEDGE_WRITE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.MEDICATION_SAFETY_USER))
            .contains(PermissionCode.RULE_WRITE, PermissionCode.KNOWLEDGE_REVIEW)
            .doesNotContain(
                PermissionCode.RULE_PUBLISH,
                PermissionCode.RULE_OVERRIDE,
                PermissionCode.KNOWLEDGE_PUBLISH);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.DIAGNOSTIC_SERVICE_USER))
            .contains(PermissionCode.TERM_WRITE, PermissionCode.EVENT_WRITE)
            .doesNotContain(
                PermissionCode.TERM_PUBLISH,
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.CONTEXT_WRITE);
    }

    @Test
    void qualityGovernorOwnsEvaluationClosedLoopWithoutClinicalOverride() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.QUALITY_GOVERNOR))
            .contains(
                PermissionCode.EVALUATION_WRITE,
                PermissionCode.EVALUATION_PUBLISH,
                PermissionCode.EVALUATION_EXECUTE,
                PermissionCode.EVALUATION_REMEDIATE,
                PermissionCode.EVALUATION_REVIEW)
            .doesNotContain(PermissionCode.RULE_OVERRIDE, PermissionCode.SYSTEM_MANAGE);
    }

    @Test
    void complianceAuditorActionsAreReadOrExportOnly() {
        var permissions = DefaultPermissionPolicy.permissionsOf(RoleCode.COMPLIANCE_AUDITOR);
        assertThat(permissions)
            .contains(PermissionCode.AUDIT_READ, PermissionCode.AUDIT_EXPORT)
            .doesNotContain(PermissionCode.MENU_IDENTITY_BINDINGS, PermissionCode.SYSTEM_MANAGE);
        assertThat(permissions)
            .filteredOn(permission -> permission.dimension() == PermissionDimension.ACTION)
            .allMatch(permission -> permission.code().endsWith(".read")
                || permission.code().endsWith(".export"));
    }

    @Test
    void integrationOperatorConnectsSystemsWithoutPublishingClinicalKnowledge() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.INTEGRATION_OPERATOR))
            .contains(
                PermissionCode.CONTEXT_WRITE,
                PermissionCode.EVENT_WRITE,
                PermissionCode.INTEGRATION_WRITE,
                PermissionCode.INTEGRATION_EXECUTE,
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.MENU_SYSTEM_PROVIDERS)
            .doesNotContain(
                PermissionCode.TERM_PUBLISH,
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.MENU_ADMIN_USERS);
    }

    @Test
    void implementationOperatorOnboardsInstitutionsWithoutPlatformOrSystemGovernance() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IMPLEMENTATION_OPERATOR))
            .contains(
                PermissionCode.TENANT_READ,
                PermissionCode.ORG_WRITE,
                PermissionCode.PACKAGE_PUBLISH,
                PermissionCode.MENU_IMPLEMENTATION_GUIDE,
                PermissionCode.MENU_TENANT_ONBOARDING)
            .doesNotContain(
                PermissionCode.PLATFORM_PUBLISH,
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.ENV_EMERGENCY);
    }

    @Test
    void everyRoleHasCatalogMenusAndMenuSpecificReadPermissions() {
        for (RoleCode role : RoleCode.values()) {
            var permissions = DefaultPermissionPolicy.permissionsOf(role);
            var menuPermissions = permissions.stream()
                .filter(permission -> permission.dimension() == PermissionDimension.MENU)
                .toList();

            assertThat(menuPermissions)
                .as("%s 必须拥有目录内的工作入口", role.code())
                .isNotEmpty()
                .allMatch(MenuPermissionCatalog::isCatalogMenuPermission);
            if (permissions.contains(PermissionCode.MENU_PATHWAY_TEMPLATES)) {
                assertThat(permissions).contains(PermissionCode.PATHWAY_READ);
            }
            if (permissions.contains(PermissionCode.MENU_RULE_DEFINITIONS)) {
                assertThat(permissions).contains(PermissionCode.RULE_READ);
            }
            if (permissions.contains(PermissionCode.MENU_TERMINOLOGY_MAPPING)) {
                assertThat(permissions).contains(PermissionCode.TERM_READ);
            }
        }
    }

    @Test
    void codesRoundtripWithoutLegacyAliases() {
        for (RoleCode role : RoleCode.values()) {
            assertThat(RoleCode.fromAuthority(role.authority())).contains(role);
            assertThat(RoleCode.fromCode(role.code())).contains(role);
        }
        for (PermissionCode permission : PermissionCode.values()) {
            assertThat(PermissionCode.fromCode(permission.code())).contains(permission);
        }
    }
}
