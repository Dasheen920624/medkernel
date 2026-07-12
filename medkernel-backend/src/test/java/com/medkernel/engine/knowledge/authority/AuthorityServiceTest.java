package com.medkernel.engine.knowledge.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/** 平台知识权威稳定身份初始化合同测试。 */
class AuthorityServiceTest {

    private static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    private static final String SECOND_AUTHORITY_ID = "mka-medkernel-cn-02";

    private AuthorityRepository repository;
    private AuditRecorder auditRecorder;
    private IsolatedAuditPublisher isolatedAudit;
    private AuthorityService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuthorityRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        service = new AuthorityService(repository, auditRecorder, isolatedAudit);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-authority", OrgScope.tenant(PlatformTenant.ID), "platform-admin"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void initializesUniquePlatformAuthorityWithStableIdentityAndAudit() {
        when(repository.findByTenantId(PlatformTenant.ID)).thenReturn(Optional.empty());
        when(repository.save(any(Authority.class))).thenAnswer(invocation -> withDatabaseIdentity(
            invocation.getArgument(0, Authority.class), 41L));

        Authority result = service.initialize(AUTHORITY_ID);

        ArgumentCaptor<Authority> persisted = ArgumentCaptor.forClass(Authority.class);
        verify(repository).save(persisted.capture());
        Authority newAuthority = persisted.getValue();
        assertThat(newAuthority.id()).isNull();
        assertThat(newAuthority.tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(newAuthority.authorityId()).isEqualTo(AUTHORITY_ID);
        assertThat(newAuthority.activeIssuerInstanceId()).isNull();
        assertThat(newAuthority.activeTrustRootFingerprint()).isNull();
        assertThat(newAuthority.handoverSequence()).isZero();
        assertThat(newAuthority.releaseSequence()).isZero();
        assertThat(newAuthority.lockVersion()).isNull();
        assertThat(newAuthority.createdAt()).isNotNull().isEqualTo(newAuthority.updatedAt());
        assertThat(newAuthority.createdBy()).isEqualTo("platform-admin");
        assertThat(newAuthority.updatedBy()).isEqualTo("platform-admin");
        assertThat(newAuthority.traceId()).isEqualTo("trace-authority");
        assertThat(result.id()).isEqualTo(41L);

        verify(auditRecorder).record(
            eq(AuditAction.CREATE),
            eq("mk_knowledge_authority"),
            eq(AUTHORITY_ID),
            contains(AUTHORITY_ID));
        verifyNoInteractions(isolatedAudit);
    }

    @Test
    void hostIpDirectoryChangeAndBackupRestoreReusePersistedAuthority() throws Exception {
        Authority persisted = authority(7L, AUTHORITY_ID);
        when(repository.findByTenantId(PlatformTenant.ID)).thenReturn(Optional.of(persisted));

        Authority beforeMigration = service.initialize(AUTHORITY_ID);
        AuthorityService restoredOnEquivalentHost =
            new AuthorityService(repository, auditRecorder, isolatedAudit);
        Authority afterMigration = restoredOnEquivalentHost.initialize(AUTHORITY_ID);

        assertThat(beforeMigration).isSameAs(persisted);
        assertThat(afterMigration).isSameAs(persisted);
        verify(repository, times(2)).findByTenantId(PlatformTenant.ID);
        verify(repository, never()).save(any());
        verifyNoInteractions(auditRecorder, isolatedAudit);

        assertThat(AuthorityService.class.getMethod("initialize", String.class).getParameterTypes())
            .containsExactly(String.class);
        assertThat(Arrays.stream(AuthorityService.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(field -> field.getType().getName()))
            .noneMatch(type -> type.contains("Environment")
                || type.contains("InetAddress")
                || type.equals("java.nio.file.Path")
                || type.equals("java.io.File"));
    }

    @Test
    void rejectsSecondAuthorityIdAndPublishesContextualFailureAudit() {
        when(repository.findByTenantId(PlatformTenant.ID))
            .thenReturn(Optional.of(authority(7L, AUTHORITY_ID)));

        assertThatThrownBy(() -> service.initialize(SECOND_AUTHORITY_ID))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                assertThat(exception.getMessage()).contains(AUTHORITY_ID, SECOND_AUTHORITY_ID);
            });

        ArgumentCaptor<AuditEvent> failure = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(failure.capture());
        AuditEvent event = failure.getValue();
        assertThat(event.action()).isEqualTo(AuditAction.CREATE);
        assertThat(event.resourceType()).isEqualTo("mk_knowledge_authority");
        assertThat(event.resourceId()).isEqualTo(AUTHORITY_ID);
        assertThat(event.actorUserId()).isEqualTo("platform-admin");
        assertThat(event.traceId()).isEqualTo("trace-authority");
        assertThat(event.outcome()).isEqualTo(AuditEvent.OUTCOME_FAILED);
        assertThat(event.errorCode()).isEqualTo(ErrorCode.CONFLICT.code());
        assertThat(event.summary()).contains(AUTHORITY_ID, SECOND_AUTHORITY_ID);
        verify(repository, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    void rejectsCustomerTenantOwnershipWithFailureAudit() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-customer", OrgScope.tenant("tenant-customer"), "customer-admin"));

        assertThatThrownBy(() -> service.initialize(AUTHORITY_ID))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.TENANT_FORBIDDEN));

        ArgumentCaptor<AuditEvent> failure = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(failure.capture());
        assertThat(failure.getValue().actorUserId()).isEqualTo("customer-admin");
        assertThat(failure.getValue().traceId()).isEqualTo("trace-customer");
        assertThat(failure.getValue().errorCode()).isEqualTo(ErrorCode.TENANT_FORBIDDEN.code());
        verifyNoInteractions(repository, auditRecorder);
    }

    @Test
    void customerTenantInvalidAuthorityIdUsesBoundedAuditTarget() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-customer", OrgScope.tenant("tenant-customer"), "customer-admin"));

        assertThatThrownBy(() -> service.initialize("x".repeat(129)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.TENANT_FORBIDDEN));

        ArgumentCaptor<AuditEvent> failure = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(failure.capture());
        assertThat(failure.getValue().resourceId()).isEqualTo("UNRESOLVED_AUTHORITY_ID");
        assertThat(failure.getValue().resourceId()).hasSizeLessThanOrEqualTo(128);
        verifyNoInteractions(repository, auditRecorder);
    }

    @Test
    void rejectsInvalidAuthorityIdBeforePersistence() {
        assertThatThrownBy(() -> service.initialize(" authority with spaces "))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        ArgumentCaptor<AuditEvent> failure = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(failure.capture());
        assertThat(failure.getValue().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED.code());
        verifyNoInteractions(repository, auditRecorder);
    }

    private Authority authority(Long id, String authorityId) {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        return new Authority(
            id,
            PlatformTenant.ID,
            authorityId,
            null,
            null,
            0,
            0,
            0L,
            now,
            "platform-admin",
            now,
            "platform-admin",
            "trace-created");
    }

    private Authority withDatabaseIdentity(Authority authority, Long id) {
        return new Authority(
            id,
            authority.tenantId(),
            authority.authorityId(),
            authority.activeIssuerInstanceId(),
            authority.activeTrustRootFingerprint(),
            authority.handoverSequence(),
            authority.releaseSequence(),
            0L,
            authority.createdAt(),
            authority.createdBy(),
            authority.updatedAt(),
            authority.updatedBy(),
            authority.traceId());
    }
}
