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
                override("t-1", RoleCode.DOCTOR, PermissionCode.RECOMMENDATION_ACCEPT, PermissionEffect.DENY),
                override("t-1", RoleCode.DOCTOR, PermissionCode.AUDIT_READ, PermissionEffect.ALLOW),
                override("t-1", RoleCode.DOCTOR, PermissionCode.MENU_COMPLIANCE_OPS, PermissionEffect.ALLOW)
            ));

        var profile = service.resolve(auth(RoleCode.DOCTOR), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes())
            .contains(PermissionCode.RECOMMENDATION_READ.code(), PermissionCode.AUDIT_READ.code())
            .doesNotContain(PermissionCode.RECOMMENDATION_ACCEPT.code());
        assertThat(profile.menuKeys()).contains("clinical-run", "compliance-ops");
    }

    @Test
    void explicitDenyWinsWhenEffectiveRolesContainConflictingOverrides() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of(assignment("t-1", "doctor-1", RoleCode.QA_MANAGER)));
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of(
                override("t-1", RoleCode.DOCTOR, PermissionCode.AUDIT_EXPORT, PermissionEffect.DENY),
                override("t-1", RoleCode.QA_MANAGER, PermissionCode.AUDIT_EXPORT, PermissionEffect.ALLOW)
            ));

        var profile = service.resolve(auth(RoleCode.DOCTOR), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes()).doesNotContain(PermissionCode.AUDIT_EXPORT.code());
    }

    @Test
    void userRoleAssignmentsAreMergedWithJwtRolesInsideTenant() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of(assignment("t-1", "doctor-1", RoleCode.QA_MANAGER)));
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var profile = service.resolve(auth(RoleCode.DOCTOR), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.roleCodes())
            .containsExactlyInAnyOrder(RoleCode.DOCTOR.code(), RoleCode.QA_MANAGER.code());
        assertThat(profile.permissionCodes())
            .contains(PermissionCode.RECOMMENDATION_ACCEPT.code(), PermissionCode.EVALUATION_PUBLISH.code());
    }

    @Test
    void scopedRoleAssignmentDoesNotGrantPermissionsOutsideCurrentDepartment() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of(assignment("t-1", "doctor-1", RoleCode.QA_MANAGER, "DEPARTMENT", "oncology")));
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var cardiologyScope = new OrgScope("t-1", null, "hospital-1", null, null, "cardiology", null);
        var profile = service.resolve(auth(RoleCode.DOCTOR), cardiologyScope, "doctor-1");

        assertThat(profile.roleCodes()).doesNotContain(RoleCode.QA_MANAGER.code());
        assertThat(profile.permissionCodes()).doesNotContain(PermissionCode.EVALUATION_PUBLISH.code());
    }

    @Test
    void clinicalDoctorOnlyReceivesClinicalNavigationRatherThanConfigurationOrAdvancedTools() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of());
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var profile = service.resolve(auth(RoleCode.DOCTOR), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.menuKeys())
            .contains("workbench", "clinical-run")
            .doesNotContain("pilot-setup", "advanced-tools");
    }

    @Test
    void evaluationExecutionPermissionExposesQualityImprovementNavigation() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of());
        when(rolePermissionRepository.findByTenantIdAndRoleCodes(eq("t-1"), anyCollection()))
            .thenReturn(List.of());

        var profile = service.resolve(auth(RoleCode.IT_OPS), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes()).contains(PermissionCode.EVALUATION_EXECUTE.code());
        assertThat(profile.menuKeys()).contains("quality-improve");
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

        var profile = service.resolve(auth(RoleCode.DOCTOR), OrgScope.tenant("t-1"), "doctor-1");

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

        var profile = service.resolve(auth(RoleCode.DOCTOR), OrgScope.tenant("t-1"), "doctor-1");

        assertThat(profile.permissionCodes()).doesNotContain(PermissionCode.ENV_EMERGENCY.code());
        assertThat(profile.environmentKeys()).contains("production").doesNotContain("emergency");
    }

    private UsernamePasswordAuthenticationToken auth(RoleCode role) {
        return new UsernamePasswordAuthenticationToken(
            "doctor-1",
            "n/a",
            List.of(new SimpleGrantedAuthority(role.authority()))
        );
    }

    private RolePermissionOverride override(
            String tenantId,
            RoleCode role,
            PermissionCode permission,
            PermissionEffect effect) {
        return new RolePermissionOverride(
            null,
            tenantId,
            role.code(),
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
