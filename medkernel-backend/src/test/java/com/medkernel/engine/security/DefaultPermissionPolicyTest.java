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
            "knowledge-governance",
            "config-packages",
            "provenance",
            "ai-workflows",
            "qc-dashboard",
            "admin-audit",
            "security-baseline",
            "implementation-guide",
            "system-providers",
            "domestic-check",
            "notification-settings")),
        Map.entry(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR, List.of(
            "workbench",
            "knowledge-governance",
            "config-packages",
            "terminology-mapping",
            "rule-definitions",
            "pathway-templates",
            "provenance",
            "graph-explore",
            "ai-workflows",
            "admin-audit")),
        Map.entry(RoleCode.ORGANIZATION_ADMIN, List.of(
            "workbench",
            "tenant-onboarding",
            "admin-users",
            "identity-bindings",
            "knowledge-governance",
            "config-packages",
            "rule-definitions",
            "pathway-templates",
            "provenance",
            "qc-dashboard",
            "admin-audit",
            "security-baseline",
            "implementation-guide",
            "system-providers",
            "domestic-check",
            "notification-settings")),
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
            "provenance",
            "graph-explore",
            "ai-workflows",
            "admin-audit")),
        Map.entry(RoleCode.CLINICAL_GOVERNOR, List.of(
            "workbench",
            "knowledge-governance",
            "rule-definitions",
            "pathway-templates",
            "provenance",
            "mpi",
            "patient-pathways",
            "cdss-fatigue",
            "workflow-todos",
            "clinical-followup",
            "sandbox",
            "qc-dashboard",
            "qc-alerts",
            "notifications")),
        Map.entry(RoleCode.CLINICAL_DECISION_USER, List.of(
            "workbench",
            "mpi",
            "patient-pathways",
            "cdss-fatigue",
            "workflow-todos",
            "clinical-followup",
            "sandbox",
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
            "provenance",
            "patient-pathways",
            "cdss-fatigue",
            "workflow-todos",
            "notifications")),
        Map.entry(RoleCode.DIAGNOSTIC_SERVICE_USER, List.of(
            "workbench",
            "terminology-mapping",
            "mpi",
            "workflow-todos",
            "notifications")),
        Map.entry(RoleCode.QUALITY_GOVERNOR, List.of(
            "workbench",
            "knowledge-governance",
            "rule-definitions",
            "provenance",
            "qc-dashboard",
            "qc-alerts",
            "insurance-audit",
            "qc-eval-sets",
            "admin-audit")),
        Map.entry(RoleCode.COMPLIANCE_AUDITOR, List.of(
            "workbench",
            "provenance",
            "admin-audit")),
        Map.entry(RoleCode.INTEGRATION_OPERATOR, List.of(
            "workbench",
            "identity-bindings",
            "terminology-mapping",
            "graph-explore",
            "ai-workflows",
            "sandbox",
            "admin-audit",
            "security-baseline",
            "adapter-hub",
            "system-providers",
            "domestic-check",
            "dev-console",
            "notification-settings")),
        Map.entry(RoleCode.IMPLEMENTATION_OPERATOR, List.of(
            "workbench",
            "tenant-onboarding",
            "admin-users",
            "identity-bindings",
            "knowledge-governance",
            "config-packages",
            "terminology-mapping",
            "provenance",
            "graph-explore",
            "ai-workflows",
            "sandbox",
            "admin-audit",
            "security-baseline",
            "implementation-guide",
            "adapter-hub",
            "system-providers",
            "domestic-check",
            "dev-console",
            "notification-settings"))
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
    void ruleCommitteeCustomerRolesCanReachRuleGovernancePage() {
        // 红线规则要求两名独立委员会成员会签，且作者不能审核自己的规则
        // （RuleGovernanceService.COMMITTEE_ROLES + validateSignoff）。在客户租户里
        // 作者=机构知识治理员被排除，唯一可承担双人独立会签的客户角色是
        // 临床治理员与质量治理员。两者既然被后端授权会签（rule.write/evaluation.publish），
        // 就必须拥有 menu.rule-definitions，否则前端路由守卫会把它们挡在规则页外，
        // 红线规则治理永远走不完院级全量。回归 P5-ACT4-01。
        for (RoleCode committeeRole : List.of(RoleCode.CLINICAL_GOVERNOR, RoleCode.QUALITY_GOVERNOR)) {
            assertThat(DefaultPermissionPolicy.permissionsOf(committeeRole))
                .as("%s 是红线规则法定委员会会签角色，必须能进入规则配置页", committeeRole.code())
                .contains(PermissionCode.MENU_RULE_DEFINITIONS)
                .contains(PermissionCode.RULE_READ);
        }
    }

    @Test
    void redLineRuleReleaseLifecycleCustomerRolesCanReachRuleGovernancePage() {
        // 红线规则要走完「创建→同行评审→双人委员会会签→影子→灰度→院级全量」全生命周期，
        // 且 validateTransition 要求作者≠会签人≠发布人。客户租户里：
        //   作者=机构知识治理员；两名独立委员=临床治理员+质量治理员；
        //   唯一既非作者亦非会签人且持 rule.publish 的发布人=机构管理员。
        // 这四个客户角色都必须拥有 menu.rule-definitions，否则前端路由守卫会让红线规则
        // 在某个环节卡死、无法经真实前台完成院级全量。回归 P5-ACT4-01 与 P5-ACT4-02。
        for (RoleCode lifecycleRole : List.of(
                RoleCode.KNOWLEDGE_GOVERNOR,
                RoleCode.CLINICAL_GOVERNOR,
                RoleCode.QUALITY_GOVERNOR,
                RoleCode.ORGANIZATION_ADMIN)) {
            assertThat(DefaultPermissionPolicy.permissionsOf(lifecycleRole))
                .as("%s 是红线规则全生命周期法定角色，必须能进入规则配置页", lifecycleRole.code())
                .contains(PermissionCode.MENU_RULE_DEFINITIONS);
        }
        // 发布人机构管理员必须持 rule.publish（canActivateFull 院级全量激活依赖）。
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.ORGANIZATION_ADMIN))
            .as("机构管理员作为红线规则发布人必须持 rule.publish")
            .contains(PermissionCode.RULE_PUBLISH);
    }

    @Test
    void pathwayFullRolloutCoordinatorCustomerRolesCanReachPathwayTemplatesPage() {
        // 路径全量/回滚门 requireReleaseCoordinator 在客户租户放行临床治理负责人或机构管理员；
        // 路径模板治理页路由守卫要求 menu.pathway-templates + pathway.read。这些法定治理/全量
        // 协调角色必须持 MENU_PATHWAY_TEMPLATES，否则机构管理员持 pathway.publish、是合法院级全量
        // 协调角色，却进不去路径模板配置页、走不完院级全量。回归 P5-ACT5-01。
        for (RoleCode coordinatorRole : List.of(
                RoleCode.KNOWLEDGE_GOVERNOR,
                RoleCode.CLINICAL_GOVERNOR,
                RoleCode.ORGANIZATION_ADMIN)) {
            assertThat(DefaultPermissionPolicy.permissionsOf(coordinatorRole))
                .as("%s 是路径治理/院级全量协调法定角色，必须能进入路径模板配置页", coordinatorRole.code())
                .contains(PermissionCode.MENU_PATHWAY_TEMPLATES)
                .contains(PermissionCode.PATHWAY_READ);
        }
        // 机构管理员作为路径院级全量协调角色必须持 pathway.publish（fullRolloutTemplate 依赖）。
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.ORGANIZATION_ADMIN))
            .as("机构管理员作为路径院级全量协调角色必须持 pathway.publish")
            .contains(PermissionCode.PATHWAY_PUBLISH);
    }

    @Test
    void sandboxRolesCanRunAndReachTheOrdinaryClinicalEntry() {
        // 全真体验沙盘的法定角色见 IA 矩阵 §3：主角色=临床决策使用者、实施运维员；
        // 次角色=临床治理负责人（验证其治理的规则/路径端到端表现）、集成运维员（验证院内系统嵌入链路）。
        for (RoleCode role : List.of(
                RoleCode.CLINICAL_DECISION_USER,
                RoleCode.IMPLEMENTATION_OPERATOR,
                RoleCode.CLINICAL_GOVERNOR,
                RoleCode.INTEGRATION_OPERATOR)) {
            assertThat(DefaultPermissionPolicy.permissionsOf(role))
                .as("%s 必须能运行并进入全真体验沙盘", role.code())
                .contains(PermissionCode.SANDBOX_RUN, PermissionCode.MENU_SANDBOX);
        }
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
    void clinicalDecisionUserCanEnterPathwayAndRecordManualOverride() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.CLINICAL_DECISION_USER))
            .contains(
                PermissionCode.RECOMMENDATION_READ,
                PermissionCode.RECOMMENDATION_ACCEPT,
                PermissionCode.RULE_READ,
                PermissionCode.RULE_OVERRIDE,
                PermissionCode.PATHWAY_READ,
                // 医师需要患者入径与节点推进权限（临床执行），但不得编辑路径模板（治理权限）。
                PermissionCode.PATHWAY_EXECUTE)
            .doesNotContain(
                PermissionCode.PATHWAY_WRITE,
                PermissionCode.RULE_WRITE,
                PermissionCode.RULE_PUBLISH,
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.SYSTEM_MANAGE);
    }

    @Test
    void nursingCollaboratorCanEnterPathwayWithoutTemplateAuthoring() {
        // 护理协作者在临床流程中需入径推进，但无权编辑模板（治理角色才有 pathway.write）。
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.NURSING_COLLABORATOR))
            .contains(PermissionCode.PATHWAY_READ, PermissionCode.PATHWAY_EXECUTE)
            .doesNotContain(PermissionCode.PATHWAY_WRITE, PermissionCode.PATHWAY_PUBLISH);
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
    void llmGovernanceActionsRestWithTheirGoverningRoles() {
        // LLM-03/07/08：provider 接入与出域治理归集成运维员，医学回归评测治理归质量与医保治理员（IA 矩阵 §9，ACTION 维度）。
        // LLM-05：全业务模型增强接入矩阵＝「模型网关全局目录」，归平台治理管理员（§9），集成运维/质量治理均不持有。
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.INTEGRATION_OPERATOR))
            .contains(PermissionCode.LLM_PROVIDER_MANAGE, PermissionCode.LLM_EGRESS_MANAGE)
            .doesNotContain(PermissionCode.LLM_EVAL_MANAGE, PermissionCode.LLM_ENHANCEMENT_MANAGE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.QUALITY_GOVERNOR))
            .contains(PermissionCode.LLM_EVAL_MANAGE)
            .doesNotContain(PermissionCode.LLM_PROVIDER_MANAGE, PermissionCode.LLM_EGRESS_MANAGE,
                PermissionCode.LLM_ENHANCEMENT_MANAGE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_GOVERNANCE_ADMIN))
            .contains(PermissionCode.LLM_ENHANCEMENT_MANAGE);
        // 临床决策用户对任何模型治理动作零授权（最小权限红线）。
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.CLINICAL_DECISION_USER))
            .doesNotContain(
                PermissionCode.LLM_PROVIDER_MANAGE,
                PermissionCode.LLM_EGRESS_MANAGE,
                PermissionCode.LLM_EVAL_MANAGE,
                PermissionCode.LLM_ENHANCEMENT_MANAGE);
    }

    @Test
    void engineDataReadRestsWithManagementAndQualityRoles() {
        // DATASVC-01：引擎数据服务层只读统计归管理质控端（质量与医保治理员，§8.4）；临床决策用户不直接消费管理统计。
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.QUALITY_GOVERNOR))
            .contains(PermissionCode.ENGINE_DATA_READ);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.CLINICAL_DECISION_USER))
            .doesNotContain(PermissionCode.ENGINE_DATA_READ);
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
            if (permissions.contains(PermissionCode.MENU_SANDBOX)) {
                assertThat(permissions).contains(PermissionCode.SANDBOX_RUN);
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
