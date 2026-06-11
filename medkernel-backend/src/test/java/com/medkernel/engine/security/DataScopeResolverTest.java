package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.security.DataAccessLevel;
import com.medkernel.shared.security.ResolvedDataScope;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeResolverTest {

    private DataScopeResolver resolver;

    @BeforeEach
    void setUp() {
        var rolePermissionRepository = Mockito.mock(RolePermissionOverrideRepository.class);
        var userRoleAssignmentRepository = Mockito.mock(UserRoleAssignmentRepository.class);
        Mockito.when(rolePermissionRepository.findByTenantIdAndRoleCodes(Mockito.anyString(), Mockito.anyCollection()))
            .thenReturn(List.of());
        Mockito.when(userRoleAssignmentRepository.findActiveByTenantIdAndUserId(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(List.of());
        resolver = new DataScopeResolver(
            new EffectivePermissionService(rolePermissionRepository, userRoleAssignmentRepository));
    }

    @Test
    void departmentScopedDoctorCanOnlyAccessSameDepartment() {
        OrgScope current = new OrgScope("t-1", null, "h-1", null, null, "cardiology", null);

        ResolvedDataScope resolved = resolver.resolve(auth(RoleCode.CLINICAL_DECISION_USER), current, "doctor-1");

        assertThat(resolved.level()).isEqualTo(DataAccessLevel.DEPARTMENT);
        assertThat(resolved.canAccess(new OrgScope("t-1", null, "h-1", null, null, "cardiology", null))).isTrue();
        assertThat(resolved.canAccess(new OrgScope("t-1", null, "h-1", null, null, "oncology", null))).isFalse();
        assertThat(resolved.canAccess(new OrgScope("t-1", null, "h-1", null, null, null, null))).isFalse();
    }

    @Test
    void wardScopedDoctorCannotCrossWardInsideSameDepartment() {
        OrgScope current = new OrgScope(
            "t-1", null, "h-1", null, null, "cardiology", "ward-1", null);

        ResolvedDataScope resolved = resolver.resolve(auth(RoleCode.CLINICAL_DECISION_USER), current, "doctor-1");

        assertThat(resolved.canAccess(new OrgScope(
            "t-1", null, "h-1", null, null, "cardiology", "ward-1", null))).isTrue();
        assertThat(resolved.canAccess(new OrgScope(
            "t-1", null, "h-1", null, null, "cardiology", "ward-2", null))).isFalse();
    }

    @Test
    void hospitalScopedRoleCanAccessSameHospitalButNotOtherHospital() {
        OrgScope current = new OrgScope("t-1", "g-1", "h-1", null, null, null, null);

        ResolvedDataScope resolved = resolver.resolve(auth(RoleCode.QUALITY_GOVERNOR), current, "qa-1");

        assertThat(resolved.level()).isEqualTo(DataAccessLevel.HOSPITAL);
        assertThat(resolved.canAccess(new OrgScope("t-1", "g-1", "h-1", null, null, "cardiology", null))).isTrue();
        assertThat(resolved.canAccess(new OrgScope("t-1", "g-1", "h-2", null, null, "cardiology", null))).isFalse();
    }

    @Test
    void groupScopedRoleCanAccessSameGroupAcrossHospitals() {
        OrgScope current = new OrgScope("t-1", "g-1", null, null, null, null, null);

        ResolvedDataScope resolved = resolver.resolve(auth(RoleCode.ORGANIZATION_ADMIN), current, "group-admin-1");

        assertThat(resolved.level()).isEqualTo(DataAccessLevel.GROUP);
        assertThat(resolved.canAccess(new OrgScope("t-1", "g-1", "h-1", null, null, "cardiology", null))).isTrue();
        assertThat(resolved.canAccess(new OrgScope("t-1", "g-2", "h-9", null, null, null, null))).isFalse();
        assertThat(resolved.canAccess(new OrgScope("t-2", "g-1", "h-1", null, null, null, null))).isFalse();
    }

    @Test
    void desensitizedPermissionDoesNotExpandRawRowAccess() {
        EffectivePermissionService permissionService = Mockito.mock(EffectivePermissionService.class);
        UsernamePasswordAuthenticationToken auth = auth(RoleCode.COMPLIANCE_AUDITOR);
        OrgScope current = OrgScope.tenant("t-1");
        Mockito.when(permissionService.effectivePermissions(auth, current, "auditor-1"))
            .thenReturn(EnumSet.of(PermissionCode.DATA_DESENSITIZED));
        DataScopeResolver desensitizedResolver = new DataScopeResolver(permissionService);

        ResolvedDataScope resolved = desensitizedResolver.resolve(auth, current, "auditor-1");

        assertThat(resolved.level()).isEqualTo(DataAccessLevel.NONE);
        assertThat(resolved.desensitized()).isTrue();
        assertThat(resolved.canAccess(new OrgScope("t-1", "g-1", "h-1", null, null, null, null))).isFalse();
    }

    private UsernamePasswordAuthenticationToken auth(RoleCode role) {
        return new UsernamePasswordAuthenticationToken(
            "user-1",
            "n/a",
            List.of(new SimpleGrantedAuthority(role.authority()))
        );
    }
}
