package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPermissionPolicyTest {

    private static final Map<RoleCode, List<String>> CUSTOMER_MENU_SNAPSHOTS = Map.ofEntries(
        Map.entry(RoleCode.PLATFORM_GOVERNANCE_ADMIN, List.of(
            "workbench",
            "tenant-onboarding",
            "admin-users",
            "identity-bindings",
            "implementation-guide",
            "knowledge-governance",
            "config-packages",
            "qc-dashboard",
            "admin-audit",
            "security-baseline",
            "system-providers",
            "notification-settings",
            "provenance",
            "ai-workflows",
            "domestic-check")),
        Map.entry(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR, List.of(
            "workbench",
            "knowledge-governance",
            "config-packages",
            "terminology-mapping",
            "rule-definitions",
            "pathway-templates",
            "admin-audit",
            "provenance",
            "graph-explore",
            "ai-workflows")),
        Map.entry(RoleCode.ORGANIZATION_ADMIN, List.of(
            "workbench",
            "tenant-onboarding",
            "admin-users",
            "identity-bindings",
            "implementation-guide",
            "knowledge-governance",
            "config-packages",
            "qc-dashboard",
            "admin-audit",
            "security-baseline",
            "system-providers",
            "notification-settings",
            "provenance",
            "domestic-check")),
        Map.entry(RoleCode.IDENTITY_ACCESS_ADMIN, List.of(
            "workbench",
            "admin-users",
            "identity-bindings",
            "admin-audit",
            "security-baseline")),
        Map.entry(RoleCode.KNOWLEDGE_GOVERNOR, List.of(
            "workbench",
            "knowledge-governance",
            "config-packages",
            "terminology-mapping",
            "rule-definitions",
            "pathway-templates",
            "admin-audit",
            "provenance",
            "graph-explore",
            "ai-workflows")),
        Map.entry(RoleCode.CLINICAL_GOVERNOR, List.of(
            "workbench",
            "knowledge-governance",
            "rule-definitions",
            "pathway-templates",
            "mpi",
            "patient-pathways",
            "cdss-fatigue",
            "workflow-todos",
            "clinical-followup",
            "qc-dashboard",
            "qc-alerts",
            "notifications",
            "provenance")),
        Map.entry(RoleCode.CLINICAL_DECISION_USER, List.of(
            "workbench",
            "mpi",
            "patient-pathways",
            "cdss-fatigue",
            "workflow-todos",
            "clinical-followup",
            "notifications")),
        Map.entry(RoleCode.NURSING_COLLABORATOR, List.of(
            "workbench",
            "mpi",
            "patient-pathways",
            "workflow-todos",
            "clinical-followup",
            "notifications")),
        Map.entry(RoleCode.MEDICATION_SAFETY_USER, List.of(
            "workbench",
            "knowledge-governance",
            "rule-definitions",
            "patient-pathways",
            "cdss-fatigue",
            "workflow-todos",
            "notifications",
            "provenance")),
        Map.entry(RoleCode.DIAGNOSTIC_SERVICE_USER, List.of(
            "workbench",
            "terminology-mapping",
            "mpi",
            "workflow-todos",
            "notifications")),
        Map.entry(RoleCode.QUALITY_GOVERNOR, List.of(
            "workbench",
            "knowledge-governance",
            "qc-dashboard",
            "qc-alerts",
            "insurance-audit",
            "qc-eval-sets",
            "admin-audit",
            "provenance")),
        Map.entry(RoleCode.COMPLIANCE_AUDITOR, List.of(
            "workbench",
            "admin-audit",
            "provenance")),
        Map.entry(RoleCode.INTEGRATION_OPERATOR, List.of(
            "workbench",
            "identity-bindings",
            "adapter-hub",
            "terminology-mapping",
            "admin-audit",
            "security-baseline",
            "system-providers",
            "notification-settings",
            "graph-explore",
            "ai-workflows",
            "domestic-check",
            "dev-console")),
        Map.entry(RoleCode.IMPLEMENTATION_OPERATOR, List.of(
            "workbench",
            "tenant-onboarding",
            "admin-users",
            "identity-bindings",
            "implementation-guide",
            "adapter-hub",
            "knowledge-governance",
            "config-packages",
            "terminology-mapping",
            "admin-audit",
            "security-baseline",
            "system-providers",
            "notification-settings",
            "provenance",
            "graph-explore",
            "ai-workflows",
            "domestic-check",
            "dev-console"))
    );

    @Test
    void customerRoleMenusMatchExactProductSnapshots() {
        assertThat(CUSTOMER_MENU_SNAPSHOTS).hasSize(14);
        CUSTOMER_MENU_SNAPSHOTS.forEach((role, expectedMenuKeys) ->
            assertThat(MenuPermissionCatalog.menuKeysFor(DefaultPermissionPolicy.permissionsOf(role)))
                .as("%s 默认菜单快照", role.code())
                .containsExactlyElementsOf(expectedMenuKeys));
    }

    @Test
    void platformGovernanceAdminHasEveryNonEmergencyNonMenuPermission() {
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);
        expected.remove(PermissionCode.ENV_EMERGENCY);
        expected.removeIf(permission -> permission.dimension() == PermissionDimension.MENU);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_GOVERNANCE_ADMIN))
            .filteredOn(permission -> permission.dimension() != PermissionDimension.MENU)
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
