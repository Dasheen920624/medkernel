package com.medkernel.compliance.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.security.EffectivePermissionService;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.auth.CredentialAdminService;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class ComplianceUserExternalRoleSyncTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "EMP-001";
    private static final String ACTOR = "integration:HIS";

    private TenantUserRepository users;
    private PlatformCredentialRepository credentials;
    private UserRoleAssignmentRepository assignments;
    private ComplianceUserService service;

    @BeforeEach
    void setUp() {
        users = mock(TenantUserRepository.class);
        credentials = mock(PlatformCredentialRepository.class);
        assignments = mock(UserRoleAssignmentRepository.class);
        when(users.findByTenantIdAndUserId(TENANT, USER)).thenReturn(Optional.of(
            new TenantUser(
                1L, TENANT, USER, "王医生", "ACTIVE", 1L,
                Instant.now(), ACTOR, Instant.now(), ACTOR, "trace")));
        when(assignments.findByTenantIdAndUserIdAndRoleCodeAndScopeLevelAndScopeCode(
            TENANT, USER, "quality-governor", "DEPARTMENT", "dept-1"))
            .thenReturn(Optional.empty());
        when(assignments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ComplianceUserService(
            users,
            credentials,
            assignments,
            mock(CredentialAdminService.class),
            mock(EffectivePermissionService.class),
            mock(AuditRecorder.class),
            mock(OrgUnitRepository.class));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-sync", OrgScope.tenant(TENANT), ACTOR));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void replacesOnlyRolesOwnedByCurrentExternalSource() {
        UserRoleAssignment sourceOwned = assignment(
            11L, "clinical-decision-user", "DEPARTMENT", "dept-1", ACTOR);
        UserRoleAssignment manuallyOwned = assignment(
            12L, "medication-safety-user", "DEPARTMENT", "dept-1", "admin-1");
        when(assignments.findActiveByTenantIdAndUserId(TENANT, USER))
            .thenReturn(List.of(sourceOwned, manuallyOwned));

        service.syncExternalRole(
            USER,
            new ComplianceUserRoleRequest(
                "quality-governor", "DEPARTMENT", "dept-1"),
            new UsernamePasswordAuthenticationToken(
                ACTOR, null,
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZATION_ADMIN"))));

        ArgumentCaptor<UserRoleAssignment> captor =
            ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(assignments, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.id()).isEqualTo(11L);
            assertThat(saved.active()).isFalse();
        }).anySatisfy(saved -> {
            assertThat(saved.roleCode()).isEqualTo("quality-governor");
            assertThat(saved.active()).isTrue();
            assertThat(saved.createdBy()).isEqualTo(ACTOR);
        });
        assertThat(captor.getAllValues())
            .noneMatch(saved -> Long.valueOf(12L).equals(saved.id()));
    }

    @Test
    void nullDesiredRoleRemovesAllRolesOwnedByCurrentExternalSource() {
        UserRoleAssignment sourceOwned = assignment(
            11L, "clinical-decision-user", "DEPARTMENT", "dept-1", ACTOR);
        when(assignments.findActiveByTenantIdAndUserId(TENANT, USER))
            .thenReturn(List.of(sourceOwned));

        service.syncExternalRole(
            USER,
            null,
            new UsernamePasswordAuthenticationToken(
                ACTOR, null,
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZATION_ADMIN"))));

        ArgumentCaptor<UserRoleAssignment> captor =
            ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(assignments).save(captor.capture());
        assertThat(captor.getValue().active()).isFalse();
        verify(assignments, never())
            .findByTenantIdAndUserIdAndRoleCodeAndScopeLevelAndScopeCode(
                any(), any(), any(), any(), any());
    }

    @Test
    void externalSyncCannotTakeOverUserOwnedByAnotherSource() {
        when(users.findByTenantIdAndUserId(TENANT, USER)).thenReturn(Optional.of(
            new TenantUser(
                1L, TENANT, USER, "旧姓名", "ACTIVE", 1L,
                Instant.now(), "integration:LIS",
                Instant.now(), "integration:LIS", "trace")));

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.syncExternalUser(USER, "王医生", "ACTIVE"))
            .isInstanceOf(com.medkernel.shared.api.error.ApiException.class)
            .hasMessageContaining("其他来源");

        verify(users, never()).save(any());
    }

    @Test
    void externalSyncCannotTakeOverPlatformCredentialUser() {
        when(credentials.findByTenantIdAndUserId(TENANT, USER))
            .thenReturn(Optional.of(mock(com.medkernel.engine.security.PlatformCredential.class)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.syncExternalUser(USER, "王医生", "ACTIVE"))
            .isInstanceOf(com.medkernel.shared.api.error.ApiException.class)
            .hasMessageContaining("平台凭证");

        verify(users, never()).save(any());
    }

    private UserRoleAssignment assignment(
            Long id,
            String role,
            String scopeLevel,
            String scopeCode,
            String createdBy) {
        Instant now = Instant.now();
        return new UserRoleAssignment(
            id, TENANT, USER, role, scopeLevel, scopeCode, "Y",
            now, createdBy, now, createdBy);
    }
}
