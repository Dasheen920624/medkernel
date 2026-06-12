package com.medkernel.engine.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.medkernel.shared.context.OrgScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class EffectivePermissionServiceTest {

    private final RolePermissionOverrideRepository rolePermissionRepository =
        Mockito.mock(RolePermissionOverrideRepository.class);
    private final UserRoleAssignmentRepository userRoleAssignmentRepository =
        Mockito.mock(UserRoleAssignmentRepository.class);
    private final EmergencyPermissionGrantRepository emergencyGrantRepository =
        Mockito.mock(EmergencyPermissionGrantRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-31T12:00:00Z"), ZoneOffset.UTC);
    private final EffectivePermissionService service =
        new EffectivePermissionService(
            rolePermissionRepository,
            userRoleAssignmentRepository,
            emergencyGrantRepository,
            clock);

    @Test
    void tenantOverrideCanDenyDefaultPermissionAndAllowExtraPermission() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of());
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of(
                override("t-1", RoleCode.CLINICAL_DECISION_USER, PermissionCode.RECOMMENDATION_ACCEPT, PermissionEffect.DENY),
                override("t-1", RoleCode.CLINICAL_DECISION_USER, PermissionCode.AUDIT_READ, PermissionEffect.ALLOW),
                override("t-1", RoleCode.CLINICAL_DECISION_USER, PermissionCode.MENU_ADMIN_AUDIT, PermissionEffect.ALLOW)
            ));

        var profile = service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes())
            .contains(PermissionCode.RECOMMENDATION_READ.code(), PermissionCode.AUDIT_READ.code())
            .doesNotContain(PermissionCode.RECOMMENDATION_ACCEPT.code());
        assertThat(profile.menuKeys()).contains("cdss-fatigue", "admin-audit");
    }

    @Test
    void explicitDenyWinsWhenEffectiveRolesContainConflictingOverrides() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of(assignment("t-1", "doctor-1", RoleCode.QUALITY_GOVERNOR)));
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of(
                override("t-1", RoleCode.CLINICAL_DECISION_USER, PermissionCode.AUDIT_EXPORT, PermissionEffect.DENY),
                override("t-1", RoleCode.QUALITY_GOVERNOR, PermissionCode.AUDIT_EXPORT, PermissionEffect.ALLOW)
            ));

        var profile = service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes()).doesNotContain(PermissionCode.AUDIT_EXPORT.code());
    }

    @Test
    void userRoleAssignmentsAreMergedWithJwtRolesInsideTenant() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of(assignment("t-1", "doctor-1", RoleCode.QUALITY_GOVERNOR)));
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var profile = service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.roleCodes())
            .containsExactlyInAnyOrder(RoleCode.CLINICAL_DECISION_USER.code(), RoleCode.QUALITY_GOVERNOR.code());
        assertThat(profile.permissionCodes())
            .contains(PermissionCode.RECOMMENDATION_ACCEPT.code(), PermissionCode.EVALUATION_PUBLISH.code());
    }

    @Test
    void scopedRoleAssignmentDoesNotGrantPermissionsOutsideCurrentDepartment() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of(assignment("t-1", "doctor-1", RoleCode.QUALITY_GOVERNOR, "DEPARTMENT", "oncology")));
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var cardiologyScope = new OrgScope("t-1", null, "hospital-1", null, null, "cardiology", null);
        var profile = service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), cardiologyScope, "doctor-1");

        assertThat(profile.roleCodes()).doesNotContain(RoleCode.QUALITY_GOVERNOR.code());
        assertThat(profile.permissionCodes()).doesNotContain(PermissionCode.EVALUATION_PUBLISH.code());
    }

    @Test
    void wardScopedRoleOnlyAppliesInsideAssignedWard() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of(assignment("t-1", "doctor-1", RoleCode.QUALITY_GOVERNOR, "WARD", "ward-a")));
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var assignedWard = new OrgScope(
            "t-1", null, "hospital-1", null, null, "cardiology", "ward-a", null);
        var anotherWard = new OrgScope(
            "t-1", null, "hospital-1", null, null, "cardiology", "ward-b", null);

        assertThat(service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), assignedWard, "doctor-1").roleCodes())
            .contains(RoleCode.QUALITY_GOVERNOR.code());
        assertThat(service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), anotherWard, "doctor-1").roleCodes())
            .doesNotContain(RoleCode.QUALITY_GOVERNOR.code());
    }

    @Test
    void clinicalDoctorOnlyReceivesClinicalNavigationRatherThanConfigurationOrAdvancedTools() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of());
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var profile = service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.menuKeys())
            .contains("workbench", "mpi", "patient-pathways", "cdss-fatigue")
            .doesNotContain("pilot-setup", "quality-improve", "advanced-tools");
    }

    @Test
    void integrationExecutionPermissionDoesNotExposeUnrelatedQualityNavigation() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of());
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var profile = service.resolve(auth(RoleCode.INTEGRATION_OPERATOR), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes()).contains(PermissionCode.EVALUATION_EXECUTE.code());
        assertThat(profile.menuKeys())
            .contains("adapter-hub", "system-providers")
            .doesNotContain("qc-dashboard");
    }

    @Test
    void activeEmergencyGrantAddsEmergencyEnvironmentUntilItExpires() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of());
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());
        when(emergencyGrantRepository.findActiveByTenantIdAndUserIdAndPermissionCode(
                "t-1",
                "doctor-1",
                PermissionCode.ENV_EMERGENCY.code(),
                clock.instant()))
            .thenReturn(List.of(grant("t-1", "doctor-1", clock.instant().plusSeconds(1800))));

        var profile = service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes()).contains(PermissionCode.ENV_EMERGENCY.code());
        assertThat(profile.environmentKeys()).contains("production", "emergency");
    }

    @Test
    void missingEmergencyGrantDoesNotExposeEmergencyEnvironment() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of());
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());
        when(emergencyGrantRepository.findActiveByTenantIdAndUserIdAndPermissionCode(
                "t-1",
                "doctor-1",
                PermissionCode.ENV_EMERGENCY.code(),
                clock.instant()))
            .thenReturn(List.of());

        var profile = service.resolve(auth(RoleCode.CLINICAL_DECISION_USER), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes()).doesNotContain(PermissionCode.ENV_EMERGENCY.code());
        assertThat(profile.environmentKeys()).contains("production").doesNotContain("emergency");
    }

    @Test
    void systemSuperAdminUsesRbacAndCannotBeDowngradedByTenantDenyOverrides() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "system-superadmin-1"))
            .thenReturn(List.of());
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of(
                override("t-1", "system-superadmin", PermissionCode.SYSTEM_MANAGE, PermissionEffect.DENY),
                override("t-1", "system-superadmin", PermissionCode.MENU_SECURITY_BASELINE, PermissionEffect.DENY),
                override("t-1", "system-superadmin", PermissionCode.ENV_EMERGENCY, PermissionEffect.DENY)
            ));

        var profile = service.resolve(
            auth("system-superadmin-1", "ROLE_SYSTEM_SUPERADMIN"),
            OrgScope.tenant("t-1"),
            "system-superadmin-1");

        assertThat(profile.roleCodes()).containsExactly("system-superadmin");
        assertThat(profile.permissionCodes())
            .contains(
                PermissionCode.SYSTEM_MANAGE.code(),
                PermissionCode.MENU_SECURITY_BASELINE.code(),
                PermissionCode.ENV_EMERGENCY.code());
        assertThat(profile.mfaRequired()).isTrue();
    }

    private UsernamePasswordAuthenticationToken auth(RoleCode role) {
        return auth("doctor-1", role.authority());
    }

    private UsernamePasswordAuthenticationToken auth(String userId, String authority) {
        return new UsernamePasswordAuthenticationToken(userId, "n/a", List.of(new SimpleGrantedAuthority(authority)));
    }

    private RolePermissionOverride override(
            String tenantId,
            RoleCode role,
            PermissionCode permission,
            PermissionEffect effect) {
        return override(tenantId, role.code(), permission, effect);
    }

    private RolePermissionOverride override(
            String tenantId,
            String roleCode,
            PermissionCode permission,
            PermissionEffect effect) {
        return new RolePermissionOverride(
            null,
            tenantId,
            roleCode,
            permission.code(),
            effect,
            null,
            "test",
            null,
            "test"
        );
    }

    private UserRoleAssignment assignment(String tenantId, String userId, RoleCode role) {
        return assignment(tenantId, userId, role, "TENANT", tenantId);
    }

    private UserRoleAssignment assignment(
            String tenantId,
            String userId,
            RoleCode role,
            String scopeLevel,
            String scopeCode) {
        return new UserRoleAssignment(
            null,
            tenantId,
            userId,
            role.code(),
            scopeLevel,
            scopeCode,
            "Y",
            null,
            "test",
            null,
            "test"
        );
    }

    private EmergencyPermissionGrant grant(String tenantId, String userId, Instant expiresAt) {
        return new EmergencyPermissionGrant(
            null,
            tenantId,
            userId,
            PermissionCode.ENV_EMERGENCY.code(),
            "抢救高危患者需要临时访问应急环境",
            "chief-1",
            clock.instant(),
            expiresAt,
            null,
            null,
            "Y",
            clock.instant(),
            "chief-1",
            clock.instant(),
            "chief-1"
        );
    }
}
