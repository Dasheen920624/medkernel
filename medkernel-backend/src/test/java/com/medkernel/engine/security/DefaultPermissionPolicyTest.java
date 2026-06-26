package com.medkernel.engine.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPermissionPolicyTest {

    private static final Pattern PERMISSION_LITERAL = Pattern.compile("'([a-z0-9.:-]+)'");

    private static final Map<String, List<String>> CUSTOMER_MENU_SNAPSHOTS = Map.of(
        "platform-admin", List.of(
            "workbench",
            "tenant-onboarding",
            "admin-users",
            "identity-bindings",
            "admin-audit",
            "security-baseline",
            "implementation-guide",
            "adapter-hub",
            "system-providers",
            "domestic-check",
            "runtime-diagnostics",
            "notifications",
            "notification-settings"),
        "engine-operator", List.of(
            "workbench",
            "knowledge-governance",
            "institution-knowledge",
            "diagnosis-knowledge",
            "runtime-releases",
            "terminology-mapping",
            "rule-definitions",
            "pathway-templates",
            "provenance",
            "graph-explore",
            "knowledge-production",
            "ai-workflows",
            "sandbox",
            "qc-dashboard",
            "qc-alerts",
            "insurance-audit",
            "qc-eval-sets",
            "admin-audit",
            "notifications",
            "notification-settings"),
        "clinical-user", List.of(
            "workbench",
            "mpi",
            "patient-pathways",
            "cdss-fatigue",
            "workflow-todos",
            "clinical-followup",
            "sandbox",
            "notifications",
            "notification-settings"),
        "auditor", List.of(
            "workbench",
            "provenance",
            "admin-audit",
            "security-baseline",
            "notifications",
            "notification-settings")
    );

    @Test
    void fourAssignableRolesJointlyCoverEveryExistingProductEntry() {
        Set<String> assignableRoleCodes = Stream.of(RoleCode.values())
            .filter(RoleCode::customerAssignable)
            .map(RoleCode::code)
            .collect(Collectors.toSet());

        assertThat(assignableRoleCodes)
            .containsExactlyInAnyOrderElementsOf(CUSTOMER_MENU_SNAPSHOTS.keySet());

        Set<String> coveredMenuKeys = CUSTOMER_MENU_SNAPSHOTS.keySet().stream()
            .map(DefaultPermissionPolicyTest::role)
            .flatMap(role -> MenuPermissionCatalog.menuKeysFor(
                DefaultPermissionPolicy.permissionsOf(role)).stream())
            .collect(Collectors.toSet());

        assertThat(coveredMenuKeys)
            .containsExactlyInAnyOrderElementsOf(MenuPermissionCatalog.allMenuKeys());
    }

    @Test
    void fourAssignableRolesJointlyCoverEveryPermissionUsedByRuntimeEndpoints() throws IOException {
        Set<String> endpointPermissions = new HashSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            sources.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> collectRuntimePermissionLiterals(path, endpointPermissions));
        }

        Set<PermissionCode> coveredPermissions = Stream.of(RoleCode.values())
            .filter(RoleCode::customerAssignable)
            .flatMap(role -> DefaultPermissionPolicy.permissionsOf(role).stream())
            .collect(Collectors.toSet());

        assertThat(endpointPermissions)
            .isNotEmpty()
            .allSatisfy(code -> {
                PermissionCode permission = PermissionCode.fromCode(code)
                    .orElseThrow(() -> new AssertionError("运行端点引用未登记权限：" + code));
                assertThat(coveredPermissions)
                    .as("四个客户角色必须覆盖运行端点权限 %s", code)
                    .contains(permission);
            });
    }

    @Test
    void eachRoleMenuMatchesItsCompleteProductResponsibility() {
        CUSTOMER_MENU_SNAPSHOTS.forEach((roleCode, expectedMenuKeys) ->
            assertThat(MenuPermissionCatalog.menuKeysFor(
                DefaultPermissionPolicy.permissionsOf(role(roleCode))))
                .as("%s 默认菜单快照", roleCode)
                .containsExactlyElementsOf(expectedMenuKeys));
    }

    @Test
    void platformAdminRunsThePlatformWithoutPublishingMedicalKnowledge() {
        assertThat(DefaultPermissionPolicy.permissionsOf(role("platform-admin")))
            .contains(
                PermissionCode.ORG_WRITE,
                PermissionCode.TENANT_READ,
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.INTEGRATION_WRITE,
                PermissionCode.MPI_READ,
                PermissionCode.MPI_WRITE)
            .doesNotContain(
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.ASSET_READ,
                PermissionCode.ASSET_WRITE,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.PATHWAY_PUBLISH,
                PermissionCode.RECOMMENDATION_ACCEPT);
    }

    @Test
    void engineOperatorOwnsKnowledgeModelAndQualityWithoutClinicalExecutionOrAccountAdministration() {
        assertThat(DefaultPermissionPolicy.permissionsOf(role("engine-operator")))
            .contains(
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.PATHWAY_PUBLISH,
                PermissionCode.CONTEXT_WRITE,
                PermissionCode.EVALUATION_EXECUTE,
                PermissionCode.LLM_PROVIDER_MANAGE,
                PermissionCode.LLM_EGRESS_MANAGE,
                PermissionCode.LLM_EVAL_MANAGE,
                PermissionCode.LLM_ENHANCEMENT_MANAGE,
                PermissionCode.SANDBOX_MANAGE)
            .doesNotContain(
                PermissionCode.ORG_WRITE,
                PermissionCode.SYSTEM_MANAGE,
                PermissionCode.RECOMMENDATION_ACCEPT,
                PermissionCode.PATHWAY_EXECUTE);
    }

    @Test
    void clinicalUserCanCompleteClinicalWorkWithoutGovernanceAuthority() {
        assertThat(DefaultPermissionPolicy.permissionsOf(role("clinical-user")))
            .contains(
                PermissionCode.RECOMMENDATION_ACCEPT,
                PermissionCode.PATHWAY_EXECUTE,
                PermissionCode.MPI_CREATE,
                PermissionCode.FOLLOWUP_WRITE,
                PermissionCode.WORKFLOW_WRITE,
                PermissionCode.SANDBOX_RUN)
            .doesNotContain(
                PermissionCode.KNOWLEDGE_WRITE,
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.RULE_WRITE,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.PATHWAY_WRITE,
                PermissionCode.PATHWAY_PUBLISH,
                PermissionCode.MPI_WRITE,
                PermissionCode.SANDBOX_MANAGE,
                PermissionCode.SYSTEM_MANAGE);
    }

    @Test
    void auditorIsReadOnlyExceptForPersonalNotificationPreferences() {
        var permissions = DefaultPermissionPolicy.permissionsOf(role("auditor"));

        assertThat(permissions)
            .contains(
                PermissionCode.AUDIT_READ,
                PermissionCode.AUDIT_EXPORT,
                PermissionCode.ASSET_READ)
            .doesNotContain(
                PermissionCode.ORG_WRITE,
                PermissionCode.ASSET_WRITE,
                PermissionCode.KNOWLEDGE_WRITE,
                PermissionCode.RULE_WRITE,
                PermissionCode.PATHWAY_WRITE,
                PermissionCode.SYSTEM_MANAGE);
        assertThat(permissions)
            .filteredOn(permission -> permission.dimension() == PermissionDimension.ACTION)
            .filteredOn(permission -> permission != PermissionCode.NOTIFICATION_WRITE)
            .allMatch(permission -> permission.code().endsWith(".read")
                || permission.code().endsWith(".export"));
    }

    @Test
    void menuEntriesCarryTheirRequiredDomainReadOrExecutionPermission() {
        CUSTOMER_MENU_SNAPSHOTS.keySet().stream()
            .map(DefaultPermissionPolicyTest::role)
            .forEach(role -> {
                var permissions = DefaultPermissionPolicy.permissionsOf(role);
                if (permissions.contains(PermissionCode.MENU_PATHWAY_TEMPLATES)
                        || permissions.contains(PermissionCode.MENU_PATIENT_PATHWAYS)) {
                    assertThat(permissions).contains(PermissionCode.PATHWAY_READ);
                }
                if (permissions.contains(PermissionCode.MENU_RULE_DEFINITIONS)) {
                    assertThat(permissions).contains(PermissionCode.RULE_READ);
                }
                if (permissions.contains(PermissionCode.MENU_TERMINOLOGY_MAPPING)) {
                    assertThat(permissions).contains(PermissionCode.TERM_READ);
                }
                if (permissions.contains(PermissionCode.MENU_SANDBOX)) {
                    assertThat(permissions).contains(PermissionCode.SANDBOX_RUN);
                }
            });
    }

    @Test
    void systemSuperadminAloneHasEmergencyPermission() {
        assertThat(DefaultPermissionPolicy.permissionsOf(role("system-superadmin")))
            .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(PermissionCode.class));

        CUSTOMER_MENU_SNAPSHOTS.keySet().stream()
            .map(DefaultPermissionPolicyTest::role)
            .forEach(role -> assertThat(DefaultPermissionPolicy.permissionsOf(role))
                .as("%s 不得获得系统紧急权限", role.code())
                .doesNotContain(PermissionCode.ENV_EMERGENCY));
    }

    private static RoleCode role(String code) {
        return RoleCode.fromCode(code)
            .orElseThrow(() -> new AssertionError("缺少职责角色：" + code));
    }

    private static void collectRuntimePermissionLiterals(Path path, Set<String> sink) {
        try {
            String source = Files.readString(path);
            if (!source.contains("@PreAuthorize") || !source.contains("@perm.has")) {
                return;
            }
            Matcher matcher = PERMISSION_LITERAL.matcher(source);
            while (matcher.find()) {
                String code = matcher.group(1);
                if (PermissionCode.fromCode(code).isPresent()) {
                    sink.add(code);
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("读取权限注解失败：" + path, exception);
        }
    }
}
