package com.medkernel.engine.security;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.medkernel.shared.context.OrgScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class EffectivePermissionServiceTest {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository =
        Mockito.mock(UserRoleAssignmentRepository.class);
    private final EffectivePermissionService service =
        new EffectivePermissionService(userRoleAssignmentRepository);

    @Test
    void userRoleAssignmentsAreMergedWithJwtRolesInsideAssignedScope() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "user-1"))
            .thenReturn(List.of(assignment(
                "t-1", "user-1", RoleCode.ENGINE_OPERATOR, "DEPARTMENT", "oncology")));

        var scope = new OrgScope(
            "t-1", "group-1", "hospital-1", null, null, "oncology", null, null);
        var profile = service.resolve(auth("user-1", RoleCode.CLINICAL_USER), scope, "user-1");

        assertThat(profile.roleCodes())
            .containsExactlyInAnyOrder(RoleCode.CLINICAL_USER.code(), RoleCode.ENGINE_OPERATOR.code());
        assertThat(profile.permissionCodes())
            .contains(PermissionCode.RECOMMENDATION_ACCEPT.code(), PermissionCode.KNOWLEDGE_PUBLISH.code());
    }

    @Test
    void scopedRoleAssignmentDoesNotGrantPermissionsOutsideAssignedDepartment() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "user-1"))
            .thenReturn(List.of(assignment(
                "t-1", "user-1", RoleCode.ENGINE_OPERATOR, "DEPARTMENT", "oncology")));

        var scope = new OrgScope(
            "t-1", "group-1", "hospital-1", null, null, "cardiology", null, null);
        var profile = service.resolve(auth("user-1", RoleCode.CLINICAL_USER), scope, "user-1");

        assertThat(profile.roleCodes()).containsExactly(RoleCode.CLINICAL_USER.code());
        assertThat(profile.permissionCodes()).doesNotContain(PermissionCode.KNOWLEDGE_PUBLISH.code());
    }

    @Test
    void wardScopedRoleOnlyAppliesInsideAssignedWard() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "user-1"))
            .thenReturn(List.of(assignment(
                "t-1", "user-1", RoleCode.AUDITOR, "WARD", "ward-a")));

        var assignedWard = new OrgScope(
            "t-1", "group-1", "hospital-1", null, null, "cardiology", "ward-a", null);
        var anotherWard = new OrgScope(
            "t-1", "group-1", "hospital-1", null, null, "cardiology", "ward-b", null);

        assertThat(service.resolve(auth("user-1", RoleCode.CLINICAL_USER), assignedWard, "user-1").roleCodes())
            .contains(RoleCode.AUDITOR.code());
        assertThat(service.resolve(auth("user-1", RoleCode.CLINICAL_USER), anotherWard, "user-1").roleCodes())
            .doesNotContain(RoleCode.AUDITOR.code());
    }

    @Test
    void clinicalUserReceivesClinicalNavigationWithoutAdministrationPages() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "doctor-1"))
            .thenReturn(List.of());

        var profile = service.resolve(
            auth("doctor-1", RoleCode.CLINICAL_USER),
            OrgScope.tenant("t-1"),
            "doctor-1");

        assertThat(profile.menuKeys())
            .contains("workbench", "mpi", "patient-pathways", "cdss-fatigue")
            .doesNotContain("tenant-onboarding", "admin-users", "admin-audit");
    }

    @Test
    void platformAdministrationDoesNotAcquireEnginePublishingOrEmergencyPermissions() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "admin-1"))
            .thenReturn(List.of());

        var profile = service.resolve(
            auth("admin-1", RoleCode.PLATFORM_ADMIN),
            OrgScope.tenant("t-1"),
            "admin-1");

        assertThat(profile.permissionCodes())
            .contains(PermissionCode.TENANT_WRITE.code(), PermissionCode.SYSTEM_MANAGE.code())
            .doesNotContain(
                PermissionCode.KNOWLEDGE_PUBLISH.code(),
                PermissionCode.EVALUATION_PUBLISH.code(),
                PermissionCode.ENV_EMERGENCY.code());
    }

    @Test
    void systemSuperAdminAlwaysUsesBuiltInCompletePermissionBundle() {
        when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "root-1"))
            .thenReturn(List.of());

        var profile = service.resolve(
            auth("root-1", RoleCode.SYSTEM_SUPERADMIN),
            OrgScope.tenant("t-1"),
            "root-1");

        assertThat(profile.roleCodes()).containsExactly(RoleCode.SYSTEM_SUPERADMIN.code());
        assertThat(profile.permissionCodes())
            .contains(
                PermissionCode.SYSTEM_MANAGE.code(),
                PermissionCode.MENU_SECURITY_BASELINE.code(),
                PermissionCode.ENV_EMERGENCY.code());
        assertThat(profile.mfaRequired()).isFalse();
    }

    private UsernamePasswordAuthenticationToken auth(String userId, RoleCode role) {
        return new UsernamePasswordAuthenticationToken(
            userId,
            "n/a",
            List.of(new SimpleGrantedAuthority(role.authority())));
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
            "test");
    }
}
