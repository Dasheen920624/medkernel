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
    void hospitalAdminLacksPlatformOps() {
        assertThat(DefaultPermissionPolicy.permissionsOf(RoleCode.HOSPITAL_ADMIN))
            .doesNotContain(PermissionCode.SYSTEM_MANAGE, PermissionCode.ENV_EMERGENCY)
            .contains(PermissionCode.RULE_PUBLISH, PermissionCode.PACKAGE_ROLLBACK);
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
    void auditComplianceIsReadOnly() {
        var perms = DefaultPermissionPolicy.permissionsOf(RoleCode.AUDIT_COMPLIANCE);
        for (PermissionCode p : perms) {
            if (p.dimension() == PermissionDimension.ACTION) {
                assertThat(p.code())
                    .as("合规审计角色动作权限仅可读 / 导出，不应有写权限：%s", p.code())
                    .matches("(.+\\.read|.+\\.export)");
            }
        }
        assertThat(perms).contains(PermissionCode.AUDIT_READ, PermissionCode.AUDIT_EXPORT);
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
}
