package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionEvaluatorTest {

    private PermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        var rolePermissionRepository = Mockito.mock(RolePermissionOverrideRepository.class);
        var userRoleAssignmentRepository = Mockito.mock(UserRoleAssignmentRepository.class);
        Mockito.when(rolePermissionRepository.findByTenantIdAndRoleCodes(Mockito.anyString(), Mockito.anyCollection()))
            .thenReturn(List.of());
        Mockito.when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(List.of());
        evaluator = new PermissionEvaluator(
            new EffectivePermissionService(rolePermissionRepository, userRoleAssignmentRepository));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthenticationMeansNoPermissions() {
        assertThat(evaluator.has("rule.publish")).isFalse();
        assertThat(evaluator.has(PermissionCode.RULE_READ)).isFalse();
    }

    @Test
    void doctorCanReadRecommendationsButNotPublishRules() {
        authenticate(RoleCode.CLINICAL_DECISION_USER);
        assertThat(evaluator.has("recommendation.read")).isTrue();
        assertThat(evaluator.has("recommendation.accept")).isTrue();
        assertThat(evaluator.has("rule.publish")).isFalse();
    }

    @Test
    void knowledgeGovernorCanPublishRulesAndKnowledge() {
        authenticate(RoleCode.KNOWLEDGE_GOVERNOR);
        assertThat(evaluator.has("rule.publish")).isTrue();
        assertThat(evaluator.has("knowledge.publish")).isTrue();
        assertThat(evaluator.has("system.manage")).isFalse();
    }

    @Test
    void platformGovernanceAdminHasAllNonMenuPermissionsAndOnlyGovernanceMenus() {
        authenticate(RoleCode.PLATFORM_GOVERNANCE_ADMIN);
        Set<PermissionCode> governanceMenus = EnumSet.of(
            PermissionCode.MENU_WORKBENCH,
            PermissionCode.MENU_TENANT_ONBOARDING,
            PermissionCode.MENU_ADMIN_USERS,
            PermissionCode.MENU_IDENTITY_BINDINGS,
            PermissionCode.MENU_IMPLEMENTATION_GUIDE,
            PermissionCode.MENU_KNOWLEDGE_GOVERNANCE,
            PermissionCode.MENU_CONFIG_PACKAGES,
            PermissionCode.MENU_QC_DASHBOARD,
            PermissionCode.MENU_ADMIN_AUDIT,
            PermissionCode.MENU_SECURITY_BASELINE,
            PermissionCode.MENU_SYSTEM_PROVIDERS,
            PermissionCode.MENU_NOTIFICATION_SETTINGS,
            PermissionCode.MENU_PROVENANCE,
            PermissionCode.MENU_AI_WORKFLOWS,
            PermissionCode.MENU_DOMESTIC_CHECK);

        for (PermissionCode perm : PermissionCode.values()) {
            if (perm == PermissionCode.ENV_EMERGENCY) {
                assertThat(evaluator.has(perm.code()))
                    .as("平台治理管理员不应拥有应急环境权限 %s", perm.code())
                    .isFalse();
                continue;
            }

            if (perm.dimension() == PermissionDimension.MENU) {
                assertThat(evaluator.has(perm.code()))
                    .as("平台治理管理员菜单 %s 应按产品信息架构快照授予", perm.code())
                    .isEqualTo(governanceMenus.contains(perm));
                continue;
            }

            assertThat(evaluator.has(perm.code()))
                .as("平台治理管理员应拥有非菜单治理权限 %s", perm.code())
                .isTrue();
        }
    }

    @Test
    void canEvaluatesDimensionAndTargetWithoutImplicitMenuShortcut() {
        authenticate(RoleCode.CLINICAL_DECISION_USER);

        assertThat(evaluator.can(PermissionDimension.MENU, "cdss-fatigue")).isTrue();
        assertThat(evaluator.can(PermissionDimension.MENU, "clinical-run")).isFalse();
        assertThat(evaluator.can(PermissionDimension.DATA, "department")).isTrue();
        assertThat(evaluator.can(PermissionDimension.DATA, "hospital")).isFalse();
    }

    @Test
    void platformAdminDoesNotGetEmergencyEnvironmentWithoutBreakGlassGrant() {
        authenticate(RoleCode.PLATFORM_GOVERNANCE_ADMIN);

        assertThat(evaluator.can(PermissionDimension.ENVIRONMENT, "emergency")).isFalse();
        assertThat(evaluator.effectivePermissions()).doesNotContain(PermissionCode.ENV_EMERGENCY);
    }

    @Test
    void multipleRolesUnion() {
        authenticate(RoleCode.CLINICAL_DECISION_USER, RoleCode.QUALITY_GOVERNOR);
        assertThat(evaluator.has("recommendation.accept")).isTrue(); // DOCTOR
        assertThat(evaluator.has("evaluation.publish")).isTrue();    // QA_MANAGER
        assertThat(evaluator.has("system.manage")).isFalse();        // neither
    }

    @Test
    void hasAnyShortCircuits() {
        authenticate(RoleCode.CLINICAL_DECISION_USER);
        assertThat(evaluator.hasAny("rule.publish", "recommendation.read")).isTrue();
        assertThat(evaluator.hasAny("rule.publish", "system.manage")).isFalse();
        assertThat(evaluator.hasAny()).isFalse();
    }

    @Test
    void hasAllRequiresEveryCode() {
        authenticate(RoleCode.CLINICAL_GOVERNOR);
        assertThat(evaluator.hasAll("rule.read", "rule.publish")).isTrue();
        assertThat(evaluator.hasAll("rule.read", "system.manage")).isFalse();
        assertThat(evaluator.hasAll()).isTrue();
    }

    @Test
    void unknownPermissionCodeReturnsFalse() {
        authenticate(RoleCode.PLATFORM_GOVERNANCE_ADMIN);
        assertThat(evaluator.has("does.not.exist")).isFalse();
    }

    private void authenticate(RoleCode... roles) {
        var authorities = java.util.Arrays.stream(roles)
            .map(r -> new SimpleGrantedAuthority(r.authority()))
            .toList();
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken("user", "creds", authorities);
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
