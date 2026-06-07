package com.medkernel.engine.security;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPermissionPolicyTest {

    @Test
    void platformAdminHasAllNonEmergencyPermissions() {
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);
        expected.remove(PermissionCode.ENV_EMERGENCY);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_ADMIN))
            .containsAll(expected)
            .doesNotContain(PermissionCode.ENV_EMERGENCY);
    }

    @Test
    void groupAdminHasAllNonEmergencyPermissions() {
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);
        expected.remove(PermissionCode.ENV_EMERGENCY);

        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.GROUP_ADMIN))
            .containsAll(expected)
            .doesNotContain(PermissionCode.ENV_EMERGENCY);
    }

    @Test
    void systemSuperAdminHasEveryRuntimePermissionIncludingEmergency() {
        RoleCode systemSuperAdmin = RoleCode.fromCode("system-superadmin").orElseThrow();
        EnumSet<PermissionCode> expected = EnumSet.allOf(PermissionCode.class);

        assertThat(DefaultPermissionPolicy.permissionsOf(systemSuperAdmin))
            .containsAll(expected)
            .contains(PermissionCode.SYSTEM_MANAGE, PermissionCode.ENV_EMERGENCY);
    }

    @Test
    void hospitalAdminLacksPlatformOps() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.HOSPITAL_ADMIN))
            .doesNotContain(PermissionCode.SYSTEM_MANAGE, PermissionCode.ENV_EMERGENCY)
            .contains(PermissionCode.RULE_PUBLISH, PermissionCode.PACKAGE_ROLLBACK);
    }

    @Test
    void workbenchReadinessValidationPermissionIsRealAndClinicalRolesCannotUseIt() {
        PermissionCode.fromCode("workbench:readiness:view")
            .ifPresentOrElse(permission -> {
                assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IMPLEMENTATION_ENGINEER))
                    .contains(permission);
                assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IT_OPS))
                    .contains(permission);
                assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.HOSPITAL_ADMIN))
                    .contains(permission);
                assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.PLATFORM_ADMIN))
                    .contains(permission);
                assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.DOCTOR))
                    .doesNotContain(permission);
                assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.NURSE))
                    .doesNotContain(permission);
            }, () -> {
                throw new AssertionError("WORKBENCH-02 动作权限 workbench:readiness:view 未登记");
            });
    }

    @Test
    void doctorCanReadAndAcceptButNotPublishRules() {
        var perms = DefaultPermissionPolicy.permissionsOf(RoleCode.DOCTOR);
        assertThat(perms)
            .contains(PermissionCode.RECOMMENDATION_READ, PermissionCode.RECOMMENDATION_ACCEPT,
                      PermissionCode.RULE_READ, PermissionCode.PATHWAY_READ)
            .doesNotContain(PermissionCode.RULE_WRITE, PermissionCode.RULE_PUBLISH,
                            PermissionCode.PATHWAY_PUBLISH, PermissionCode.SYSTEM_MANAGE);
    }

    @Test
    void modelExecutionAndTenantPolicyManagementUseSeparatePermissions() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.DOCTOR))
            .contains(PermissionCode.LLM_READ, PermissionCode.LLM_EXECUTE)
            .doesNotContain(PermissionCode.LLM_MANAGE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.NURSE))
            .contains(PermissionCode.LLM_READ, PermissionCode.LLM_EXECUTE)
            .doesNotContain(PermissionCode.LLM_MANAGE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.MEDICAL_AFFAIRS))
            .contains(PermissionCode.LLM_READ, PermissionCode.LLM_EXECUTE)
            .doesNotContain(PermissionCode.LLM_MANAGE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.QA_MANAGER))
            .contains(PermissionCode.LLM_READ)
            .doesNotContain(PermissionCode.LLM_EXECUTE, PermissionCode.LLM_MANAGE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IT_OPS))
            .contains(PermissionCode.LLM_READ, PermissionCode.LLM_EXECUTE, PermissionCode.LLM_MANAGE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IMPLEMENTATION_ENGINEER))
            .contains(PermissionCode.LLM_READ, PermissionCode.LLM_EXECUTE, PermissionCode.LLM_MANAGE);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IT_OPS))
            .contains(PermissionCode.MENU_AI_WORKFLOWS);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IMPLEMENTATION_ENGINEER))
            .contains(PermissionCode.MENU_AI_WORKFLOWS);
    }

    @Test
    void auditComplianceIsReadOnly() {
        var perms = DefaultPermissionPolicy.permissionsOf(RoleCode.AUDIT_COMPLIANCE);
        for (PermissionCode p : perms) {
            if (p.dimension() == PermissionDimension.ACTION) {
                assertThat(p.code())
                    .as("合规审计角色动作权限仅可读 / 导出，不应有写权限：%s", p.code())
                    .matches("(.+\\.read|.+\\.export)");
            }
        }
        assertThat(perms)
            .contains(PermissionCode.AUDIT_READ, PermissionCode.AUDIT_EXPORT)
            .doesNotContain(PermissionCode.MENU_SYSTEM_PROVIDERS);
    }

    @Test
    void onlyAdministratorRolesReceiveUserManagementMenu() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.HOSPITAL_ADMIN))
            .contains(PermissionCode.MENU_ADMIN_USERS);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IT_OPS))
            .doesNotContain(PermissionCode.MENU_ADMIN_USERS);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.AUDIT_COMPLIANCE))
            .doesNotContain(PermissionCode.MENU_ADMIN_USERS);
    }

    @Test
    void medicalAffairsCanPublishKnowledgeAndPathways() {
        var perms = DefaultPermissionPolicy.permissionsOf(RoleCode.MEDICAL_AFFAIRS);
        assertThat(perms).contains(
            PermissionCode.KNOWLEDGE_REVIEW, PermissionCode.KNOWLEDGE_PUBLISH,
            PermissionCode.KNOWLEDGE_WITHDRAW, PermissionCode.KNOWLEDGE_EXPORT,
            PermissionCode.PATHWAY_PUBLISH, PermissionCode.RULE_PUBLISH);
        assertThat(perms).doesNotContain(PermissionCode.SYSTEM_MANAGE);
    }

    @Test
    void doctorCannotWithdrawOrPublishKnowledge() {
        var perms = DefaultPermissionPolicy.permissionsOf(RoleCode.DOCTOR);
        assertThat(perms)
            .contains(PermissionCode.KNOWLEDGE_READ)
            .doesNotContain(
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.KNOWLEDGE_WITHDRAW,
                PermissionCode.KNOWLEDGE_REVIEW);
    }

    @Test
    void auditComplianceCanExportKnowledgeButNotWrite() {
        var perms = DefaultPermissionPolicy.permissionsOf(RoleCode.AUDIT_COMPLIANCE);
        assertThat(perms)
            .contains(PermissionCode.KNOWLEDGE_READ, PermissionCode.KNOWLEDGE_EXPORT)
            .doesNotContain(
                PermissionCode.KNOWLEDGE_WRITE,
                PermissionCode.KNOWLEDGE_PUBLISH,
                PermissionCode.KNOWLEDGE_WITHDRAW);
    }

    @Test
    void provenanceRolesHaveBothMenuAndKnowledgeReadPermission() {
        for (RoleCode role : new RoleCode[]{
            RoleCode.IT_OPS,
            RoleCode.MEDICAL_AFFAIRS,
            RoleCode.QA_MANAGER,
            RoleCode.SPECIALIST,
            RoleCode.AUDIT_COMPLIANCE,
            RoleCode.IMPLEMENTATION_ENGINEER}) {
            assertThat(DefaultPermissionPolicy.permissionsOf(role))
                .as("%s 的来源追溯入口和读取权限必须成对授权", role.code())
                .contains(PermissionCode.MENU_PROVENANCE, PermissionCode.KNOWLEDGE_READ);
        }
    }

    @Test
    void roleCodeRoundtripsThroughAuthority() {
        for (RoleCode role : RoleCode.values()) {
            assertThat(RoleCode.fromAuthority(role.authority())).contains(role);
            assertThat(RoleCode.fromCode(role.code())).contains(role);
        }
    }

    @Test
    void permissionCodeRoundtrip() {
        for (PermissionCode perm : PermissionCode.values()) {
            assertThat(PermissionCode.fromCode(perm.code())).contains(perm);
        }
    }

    @Test
    void clinicalRolesCanReadContextButNotWrite() {
        for (RoleCode role : new RoleCode[]{
            RoleCode.DOCTOR, RoleCode.NURSE, RoleCode.SPECIALIST, RoleCode.DEPT_HEAD}) {
            var perms = DefaultPermissionPolicy.permissionsOf(role);
            assertThat(perms)
                .as("%s 应能读临床上下文", role)
                .contains(PermissionCode.CONTEXT_READ);
            assertThat(perms)
                .as("%s 不应能写临床上下文", role)
                .doesNotContain(PermissionCode.CONTEXT_WRITE);
        }
    }

    @Test
    void integrationRolesCanWriteContext() {
        for (RoleCode role : new RoleCode[]{
            RoleCode.IT_OPS, RoleCode.IMPLEMENTATION_ENGINEER}) {
            var perms = DefaultPermissionPolicy.permissionsOf(role);
            assertThat(perms)
                .as("%s 数据接入角色应同时具备读写", role)
                .contains(PermissionCode.CONTEXT_READ, PermissionCode.CONTEXT_WRITE);
        }
    }

    @Test
    void medicalAffairsAndQaCanReadContextOnly() {
        for (RoleCode role : new RoleCode[]{
            RoleCode.MEDICAL_AFFAIRS, RoleCode.QA_MANAGER, RoleCode.AUDIT_COMPLIANCE}) {
            var perms = DefaultPermissionPolicy.permissionsOf(role);
            assertThat(perms).contains(PermissionCode.CONTEXT_READ);
            assertThat(perms)
                .as("%s 仅读上下文，不应能写", role)
                .doesNotContain(PermissionCode.CONTEXT_WRITE);
        }
    }

    @Test
    void clinicalAndGovernanceRolesCanReadClinicalEvents() {
        for (RoleCode role : new RoleCode[]{
            RoleCode.DOCTOR, RoleCode.NURSE, RoleCode.SPECIALIST, RoleCode.DEPT_HEAD,
            RoleCode.MEDICAL_AFFAIRS, RoleCode.QA_MANAGER, RoleCode.AUDIT_COMPLIANCE,
            RoleCode.IT_OPS, RoleCode.IMPLEMENTATION_ENGINEER}) {
            assertThat(DefaultPermissionPolicy.permissionsOf(role))
                .as("%s 应能查看临床事件诊断信息", role)
                .contains(PermissionCode.EVENT_READ);
        }
    }

    @Test
    void integrationAndAdminRolesCanWriteClinicalEvents() {
        for (RoleCode role : new RoleCode[]{
            RoleCode.IT_OPS, RoleCode.IMPLEMENTATION_ENGINEER,
            RoleCode.HOSPITAL_ADMIN, RoleCode.GROUP_ADMIN, RoleCode.PLATFORM_ADMIN}) {
            assertThat(DefaultPermissionPolicy.permissionsOf(role))
                .as("%s 应能写入临床事件", role)
                .contains(PermissionCode.EVENT_READ, PermissionCode.EVENT_WRITE);
        }
    }

    @Test
    void clinicalRolesCannotWriteClinicalEvents() {
        for (RoleCode role : new RoleCode[]{
            RoleCode.DOCTOR, RoleCode.NURSE, RoleCode.SPECIALIST, RoleCode.DEPT_HEAD,
            RoleCode.MEDICAL_AFFAIRS, RoleCode.QA_MANAGER, RoleCode.AUDIT_COMPLIANCE}) {
            assertThat(DefaultPermissionPolicy.permissionsOf(role))
                .as("%s 不应能写入临床事件", role)
                .doesNotContain(PermissionCode.EVENT_WRITE);
        }
    }

    @Test
    void evaluationClosedLoopHasSeparatedPermissions() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.QA_MANAGER))
            .contains(
                PermissionCode.EVALUATION_EXECUTE,
                PermissionCode.EVALUATION_REMEDIATE,
                PermissionCode.EVALUATION_REVIEW);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.IT_OPS))
            .contains(PermissionCode.EVALUATION_EXECUTE)
            .doesNotContain(PermissionCode.EVALUATION_REVIEW);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.DEPT_HEAD))
            .contains(PermissionCode.EVALUATION_REMEDIATE)
            .doesNotContain(PermissionCode.EVALUATION_REVIEW);
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.DOCTOR))
            .doesNotContain(
                PermissionCode.EVALUATION_REMEDIATE,
                PermissionCode.EVALUATION_REVIEW);
    }

    @Test
    void everyRoleUsesOnlyInfrafiveSecondLevelMenuPermissions() {
        for (RoleCode role : RoleCode.values()) {
            var menuPermissions = DefaultPermissionPolicy.permissionsOf(role).stream()
                .filter(permission -> permission.dimension() == PermissionDimension.MENU)
                .toList();

            assertThat(menuPermissions)
                .as("%s 必须拥有至少一个二级菜单权限", role.code())
                .isNotEmpty();
            assertThat(menuPermissions)
                .as("%s 菜单权限必须全部来自 INFRA-05 32 项目录", role.code())
                .allMatch(MenuPermissionCatalog::isCatalogMenuPermission);
        }
    }

    @Test
    void doctorNavigationIsClinicalOnlyAtSecondLevelGranularity() {
        assertThat(MenuPermissionCatalog.menuKeysFor(DefaultPermissionPolicy.permissionsOf(RoleCode.DOCTOR)))
            .containsExactlyInAnyOrder(
                "workbench",
                "mpi",
                "patient-pathways",
                "cdss-fatigue",
                "rule-validate",
                "workflow-todos",
                "notifications",
                "clinical-followup")
            .doesNotContain("pilot-setup", "quality-improve", "advanced-tools");
    }
}
