package com.medkernel.compliance.identitybinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

class IdentityBindingExternalSyncTest {

    private static final String TENANT = "tenant-1";
    private static final String USER = "EMP-001";
    private static final String ACTOR = "integration:HIS";

    private IdentityBindingRepository repository;
    private SmCryptoService crypto;
    private IdentityBindingService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityBindingRepository.class);
        TenantUserRepository users = mock(TenantUserRepository.class);
        crypto = mock(SmCryptoService.class);
        when(users.findByTenantIdAndUserId(TENANT, USER)).thenReturn(Optional.of(
            new TenantUser(
                1L, TENANT, USER, "王医生", "ACTIVE", 1L,
                Instant.now(), ACTOR, Instant.now(), ACTOR, "trace")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new IdentityBindingService(
            repository, users, crypto, mock(AuditRecorder.class));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-sync", OrgScope.tenant(TENANT), ACTOR));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void replacesOnlyIdentityOwnedByCurrentExternalSource() {
        IdentityBinding sourceOwned = binding(
            1L, "idb-source", "EMPLOYEE_NO", "sm3:old", "ACTIVE", ACTOR);
        IdentityBinding manuallyOwned = binding(
            2L, "idb-manual", "OIDC", "sm3:manual", "ACTIVE", "admin-1");
        when(repository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(TENANT, USER))
            .thenReturn(List.of(sourceOwned, manuallyOwned));
        when(crypto.sm3Hex("EMP-002")).thenReturn("new");
        when(repository.findByTenantIdAndProviderTypeAndExternalSubjectDigest(
            TENANT, "EMPLOYEE_NO", "sm3:new")).thenReturn(Optional.empty());
        when(repository.findByTenantIdAndUserIdAndProviderTypeAndStatus(
            TENANT, USER, "EMPLOYEE_NO", "ACTIVE")).thenReturn(Optional.empty());

        IdentityBindingResponse response = service.syncExternalIdentity(
            TENANT, USER, IdentityProviderType.EMPLOYEE_NO, "EMP-002");

        assertThat(response.providerType()).isEqualTo("EMPLOYEE_NO");
        assertThat(response.status()).isEqualTo("ACTIVE");
        ArgumentCaptor<IdentityBinding> captor = ArgumentCaptor.forClass(IdentityBinding.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.bindingId()).isEqualTo("idb-source");
            assertThat(saved.status()).isEqualTo("UNBOUND");
        }).anySatisfy(saved -> {
            assertThat(saved.providerType()).isEqualTo("EMPLOYEE_NO");
            assertThat(saved.externalSubjectDigest()).isEqualTo("sm3:new");
            assertThat(saved.status()).isEqualTo("ACTIVE");
            assertThat(saved.createdBy()).isEqualTo(ACTOR);
        });
        assertThat(captor.getAllValues())
            .noneMatch(saved -> "idb-manual".equals(saved.bindingId()));
    }

    @Test
    void emptyDesiredIdentityRemovesAllBindingsOwnedByCurrentExternalSource() {
        IdentityBinding sourceOwned = binding(
            1L, "idb-source", "EMPLOYEE_NO", "sm3:old", "ACTIVE", ACTOR);
        IdentityBinding manuallyOwned = binding(
            2L, "idb-manual", "OIDC", "sm3:manual", "ACTIVE", "admin-1");
        when(repository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(TENANT, USER))
            .thenReturn(List.of(sourceOwned, manuallyOwned));

        IdentityBindingResponse response = service.syncExternalIdentity(
            TENANT, USER, null, null);

        assertThat(response).isNull();
        ArgumentCaptor<IdentityBinding> captor = ArgumentCaptor.forClass(IdentityBinding.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().bindingId()).isEqualTo("idb-source");
        assertThat(captor.getValue().status()).isEqualTo("UNBOUND");
    }

    @Test
    void rejectsDesiredIdentityOwnedByAnotherSource() {
        IdentityBinding otherSource = binding(
            3L, "idb-other", "EMPLOYEE_NO", "sm3:new", "ACTIVE", "integration:LIS");
        when(repository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(TENANT, USER))
            .thenReturn(List.of(otherSource));
        when(crypto.sm3Hex("EMP-002")).thenReturn("new");
        when(repository.findByTenantIdAndProviderTypeAndExternalSubjectDigest(
            TENANT, "EMPLOYEE_NO", "sm3:new")).thenReturn(Optional.of(otherSource));

        assertThatThrownBy(() -> service.syncExternalIdentity(
            TENANT, USER, IdentityProviderType.EMPLOYEE_NO, "EMP-002"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("其他来源");

        verify(repository, never()).save(any());
    }

    private IdentityBinding binding(
            Long id,
            String bindingId,
            String provider,
            String digest,
            String status,
            String createdBy) {
        Instant now = Instant.now();
        return new IdentityBinding(
            id, bindingId, TENANT, USER, provider, digest, "****0001", status, 1L,
            null, now, createdBy, now, createdBy, "trace");
    }
}
