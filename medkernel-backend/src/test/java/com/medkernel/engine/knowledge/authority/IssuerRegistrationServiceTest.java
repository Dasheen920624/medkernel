package com.medkernel.engine.knowledge.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

/** 发布实例独立身份与独立签名密钥登记合同测试。 */
class IssuerRegistrationServiceTest {

    private static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    private static final String ISSUER_A = "issuer-platform-134";
    private static final String ISSUER_B = "issuer-platform-future-01";
    private static final String KEY_A = "kms:key:issuer-134";
    private static final String KEY_B = "kms:key:issuer-future-01";
    private static final String ROOT_FINGERPRINT = "sm3:" + "a".repeat(64);
    private static final String PUBLIC_KEY_A = "sm3:" + "b".repeat(64);
    private static final String PUBLIC_KEY_B = "sm3:" + "c".repeat(64);
    private static final Instant NOT_BEFORE = Instant.parse("2026-07-12T00:00:00Z");
    private static final Instant NOT_AFTER = Instant.parse("2027-07-12T00:00:00Z");

    private AuthorityRepository authorities;
    private IssuerInstanceRepository issuers;
    private SigningKeyRepository signingKeys;
    private TrustRootRepository trustRoots;
    private SigningKeyPort signingKeyPort;
    private AuditRecorder auditRecorder;
    private IsolatedAuditPublisher isolatedAudit;
    private IssuerRegistrationService service;

    @BeforeEach
    void setUp() {
        authorities = mock(AuthorityRepository.class);
        issuers = mock(IssuerInstanceRepository.class);
        signingKeys = mock(SigningKeyRepository.class);
        trustRoots = mock(TrustRootRepository.class);
        signingKeyPort = mock(SigningKeyPort.class);
        auditRecorder = mock(AuditRecorder.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        service = new IssuerRegistrationService(
            authorities,
            issuers,
            signingKeys,
            trustRoots,
            signingKeyPort,
            auditRecorder,
            isolatedAudit);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-issuer", OrgScope.tenant(PlatformTenant.ID), "platform-admin"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void registersStandbyIssuerWithIndependentSigningKeyAndAudit() {
        arrangeNewIssuer(provisionedKey(ISSUER_B, KEY_B, PUBLIC_KEY_B, "CERTIFICATE-B"));
        when(issuers.save(any(IssuerInstance.class))).thenAnswer(invocation ->
            withIssuerDatabaseIdentity(invocation.getArgument(0, IssuerInstance.class), 42L));
        when(signingKeys.save(any(SigningKey.class))).thenAnswer(invocation ->
            withKeyDatabaseIdentity(invocation.getArgument(0, SigningKey.class), 84L));

        IssuerRegistrationService.Registration result =
            service.register(ISSUER_B, "未来平台知识发布实例");

        ArgumentCaptor<IssuerInstance> issuer = ArgumentCaptor.forClass(IssuerInstance.class);
        ArgumentCaptor<SigningKey> key = ArgumentCaptor.forClass(SigningKey.class);
        verify(issuers).save(issuer.capture());
        verify(signingKeys).save(key.capture());

        assertThat(issuer.getValue().tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(issuer.getValue().authorityId()).isEqualTo(AUTHORITY_ID);
        assertThat(issuer.getValue().issuerInstanceId()).isEqualTo(ISSUER_B);
        assertThat(issuer.getValue().displayName()).isEqualTo("未来平台知识发布实例");
        assertThat(issuer.getValue().status()).isEqualTo(IssuerInstanceStatus.STANDBY);
        assertThat(issuer.getValue().lastHandoverSequence()).isEqualTo(3);
        assertThat(issuer.getValue().activatedAt()).isNull();
        assertThat(issuer.getValue().frozenAt()).isNull();
        assertThat(issuer.getValue().handedOverAt()).isNull();
        assertThat(issuer.getValue().lockVersion()).isNull();
        assertThat(issuer.getValue().createdBy()).isEqualTo("platform-admin");
        assertThat(issuer.getValue().traceId()).isEqualTo("trace-issuer");

        assertThat(key.getValue().tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(key.getValue().authorityId()).isEqualTo(AUTHORITY_ID);
        assertThat(key.getValue().issuerInstanceId()).isEqualTo(ISSUER_B);
        assertThat(key.getValue().keyId()).isEqualTo(KEY_B);
        assertThat(key.getValue().rootFingerprint()).isEqualTo(ROOT_FINGERPRINT);
        assertThat(key.getValue().certificateChainPem()).isEqualTo("CERTIFICATE-B");
        assertThat(key.getValue().status()).isEqualTo(SigningKeyStatus.STANDBY);
        assertThat(key.getValue().notBefore()).isEqualTo(NOT_BEFORE);
        assertThat(key.getValue().notAfter()).isEqualTo(NOT_AFTER);
        assertThat(key.getValue().authorizedFromHandoverSequence()).isEqualTo(3);
        assertThat(key.getValue().authorizedThroughHandoverSequence()).isNull();
        assertThat(key.getValue().lockVersion()).isNull();
        assertThat(key.getValue().createdBy()).isEqualTo("platform-admin");
        assertThat(key.getValue().traceId()).isEqualTo("trace-issuer");

        assertThat(result.issuer().id()).isEqualTo(42L);
        assertThat(result.signingKey().id()).isEqualTo(84L);
        verify(auditRecorder).record(
            eq(AuditAction.CREATE),
            eq("mk_knowledge_issuer_instance"),
            eq(ISSUER_B),
            contains(KEY_B));
        verifyNoInteractions(isolatedAudit);
    }

    @Test
    void repeatedRegistrationReturnsPersistedBindingWithoutProvisioningAgain() {
        IssuerInstance existingIssuer = issuer(42L, ISSUER_B);
        SigningKey existingKey = signingKey(84L, ISSUER_B, KEY_B, "CERTIFICATE-B");
        when(authorities.findByTenantId(PlatformTenant.ID)).thenReturn(Optional.of(authority()));
        when(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_B)).thenReturn(Optional.of(existingIssuer));
        when(signingKeys.findByTenantIdAndAuthorityIdAndIssuerInstanceIdOrderByCreatedAtAscIdAsc(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_B)).thenReturn(List.of(existingKey));

        IssuerRegistrationService.Registration result =
            service.register(ISSUER_B, "重试时改变的展示名不重建实例");

        assertThat(result.issuer()).isSameAs(existingIssuer);
        assertThat(result.signingKey()).isSameAs(existingKey);
        verify(issuers, never()).save(any());
        verify(signingKeys, never()).save(any());
        verifyNoInteractions(signingKeyPort, trustRoots, auditRecorder, isolatedAudit);
    }

    @Test
    void rejectsKeyIdAlreadyBoundToAnotherIssuer() {
        SigningKey existingKey = signingKey(21L, ISSUER_A, KEY_A, "CERTIFICATE-A");
        arrangeNewIssuer(provisionedKey(ISSUER_B, KEY_A, PUBLIC_KEY_B, "CERTIFICATE-B"));
        when(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            PlatformTenant.ID, AUTHORITY_ID, KEY_A)).thenReturn(Optional.of(existingKey));

        assertConflict(() -> service.register(ISSUER_B, "未来平台知识发布实例"),
            KEY_A, ISSUER_A, ISSUER_B);

        assertConflictAudit(KEY_A, ISSUER_A, ISSUER_B);
        verify(issuers, never()).save(any());
        verify(signingKeys, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    void rejectsPublicKeyMaterialAlreadyBoundToAnotherIssuer() {
        SigningKey existingKey = signingKey(21L, ISSUER_A, KEY_A, "CERTIFICATE-A");
        arrangeNewIssuer(provisionedKey(ISSUER_B, KEY_B, PUBLIC_KEY_A, "CERTIFICATE-B-REISSUED"));
        when(signingKeys.findByTenantIdAndAuthorityIdOrderByCreatedAtAscIdAsc(
            PlatformTenant.ID, AUTHORITY_ID)).thenReturn(List.of(existingKey));
        when(signingKeyPort.publicKeyFingerprint("CERTIFICATE-A")).thenReturn(PUBLIC_KEY_A);

        assertConflict(() -> service.register(ISSUER_B, "未来平台知识发布实例"),
            PUBLIC_KEY_A, ISSUER_A, ISSUER_B);

        assertConflictAudit(PUBLIC_KEY_A, ISSUER_A, ISSUER_B);
        verify(issuers, never()).save(any());
        verify(signingKeys, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    void rejectsProvisionedKeyBoundToDifferentIssuer() {
        arrangeNewIssuer(provisionedKey(ISSUER_A, KEY_B, PUBLIC_KEY_B, "CERTIFICATE-B"));

        assertConflict(() -> service.register(ISSUER_B, "未来平台知识发布实例"),
            ISSUER_A, ISSUER_B);

        verify(issuers, never()).save(any());
        verify(signingKeys, never()).save(any());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    void rejectsCustomerTenantBeforeProvisioning() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-customer", OrgScope.tenant("tenant-customer"), "customer-admin"));

        assertThatThrownBy(() -> service.register(ISSUER_B, "客户实例"))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.TENANT_FORBIDDEN));

        ArgumentCaptor<AuditEvent> failure = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(failure.capture());
        assertThat(failure.getValue().actorUserId()).isEqualTo("customer-admin");
        assertThat(failure.getValue().traceId()).isEqualTo("trace-customer");
        assertThat(failure.getValue().errorCode()).isEqualTo(ErrorCode.TENANT_FORBIDDEN.code());
        verifyNoInteractions(authorities, issuers, signingKeys, trustRoots, signingKeyPort, auditRecorder);
    }

    @Test
    void signingKeyPortExposesOnlyPublicKeyMetadata() {
        assertThat(SigningKeyPort.ProvisionedSigningKey.class.isRecord()).isTrue();
        assertThat(Arrays.stream(SigningKeyPort.ProvisionedSigningKey.class.getRecordComponents())
            .map(RecordComponent::getName))
            .containsExactly(
                "authorityId",
                "issuerInstanceId",
                "keyId",
                "rootFingerprint",
                "certificateChainPem",
                "publicKeyFingerprint",
                "notBefore",
                "notAfter")
            .noneMatch(IssuerRegistrationServiceTest::looksLikePrivateMaterial);
    }

    private void arrangeNewIssuer(SigningKeyPort.ProvisionedSigningKey provisionedKey) {
        when(authorities.findByTenantId(PlatformTenant.ID)).thenReturn(Optional.of(authority()));
        when(issuers.findByTenantIdAndAuthorityIdAndIssuerInstanceId(
            PlatformTenant.ID, AUTHORITY_ID, ISSUER_B)).thenReturn(Optional.empty());
        when(signingKeyPort.provisionSigningKey(AUTHORITY_ID, ISSUER_B)).thenReturn(provisionedKey);
        when(signingKeyPort.publicKeyFingerprint(provisionedKey.certificateChainPem()))
            .thenReturn(provisionedKey.publicKeyFingerprint());
        when(signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
            PlatformTenant.ID, AUTHORITY_ID, provisionedKey.keyId())).thenReturn(Optional.empty());
        when(signingKeys.findByTenantIdAndAuthorityIdOrderByCreatedAtAscIdAsc(
            PlatformTenant.ID, AUTHORITY_ID)).thenReturn(List.of());
        when(trustRoots.findByTenantIdAndAuthorityIdAndRootFingerprint(
            PlatformTenant.ID, AUTHORITY_ID, ROOT_FINGERPRINT)).thenReturn(Optional.of(trustRoot()));
    }

    private void assertConflict(Runnable invocation, String... context) {
        assertThatThrownBy(invocation::run)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                assertThat(exception.getMessage()).contains(context);
            });
    }

    private void assertConflictAudit(String... context) {
        ArgumentCaptor<AuditEvent> failure = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(failure.capture());
        assertThat(failure.getValue().resourceType()).isEqualTo("mk_knowledge_issuer_instance");
        assertThat(failure.getValue().resourceId()).isEqualTo(ISSUER_B);
        assertThat(failure.getValue().outcome()).isEqualTo(AuditEvent.OUTCOME_FAILED);
        assertThat(failure.getValue().errorCode()).isEqualTo(ErrorCode.CONFLICT.code());
        assertThat(failure.getValue().summary()).contains(context);
    }

    private Authority authority() {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        return new Authority(
            7L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            ISSUER_A,
            ROOT_FINGERPRINT,
            3,
            17,
            2L,
            now,
            "platform-admin",
            now,
            "platform-admin",
            "trace-authority");
    }

    private TrustRoot trustRoot() {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        return new TrustRoot(
            11L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            ROOT_FINGERPRINT,
            "ROOT-CERTIFICATE",
            null,
            0,
            TrustRootStatus.ACTIVE,
            now,
            Instant.parse("2036-07-11T00:00:00Z"),
            null,
            null,
            0L,
            now,
            "platform-admin",
            now,
            "platform-admin",
            "trace-root");
    }

    private IssuerInstance issuer(Long id, String issuerInstanceId) {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        return new IssuerInstance(
            id,
            PlatformTenant.ID,
            AUTHORITY_ID,
            issuerInstanceId,
            "平台知识发布实例",
            IssuerInstanceStatus.STANDBY,
            3,
            null,
            null,
            null,
            0L,
            now,
            "platform-admin",
            now,
            "platform-admin",
            "trace-created");
    }

    private SigningKey signingKey(
            Long id,
            String issuerInstanceId,
            String keyId,
            String certificateChainPem) {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        return new SigningKey(
            id,
            PlatformTenant.ID,
            AUTHORITY_ID,
            issuerInstanceId,
            keyId,
            ROOT_FINGERPRINT,
            certificateChainPem,
            SigningKeyStatus.STANDBY,
            NOT_BEFORE,
            NOT_AFTER,
            3,
            null,
            0L,
            now,
            "platform-admin",
            now,
            "platform-admin",
            "trace-created");
    }

    private SigningKeyPort.ProvisionedSigningKey provisionedKey(
            String issuerInstanceId,
            String keyId,
            String publicKeyFingerprint,
            String certificateChainPem) {
        return new SigningKeyPort.ProvisionedSigningKey(
            AUTHORITY_ID,
            issuerInstanceId,
            keyId,
            ROOT_FINGERPRINT,
            certificateChainPem,
            publicKeyFingerprint,
            NOT_BEFORE,
            NOT_AFTER);
    }

    private IssuerInstance withIssuerDatabaseIdentity(IssuerInstance issuer, Long id) {
        return new IssuerInstance(
            id,
            issuer.tenantId(),
            issuer.authorityId(),
            issuer.issuerInstanceId(),
            issuer.displayName(),
            issuer.status(),
            issuer.lastHandoverSequence(),
            issuer.activatedAt(),
            issuer.frozenAt(),
            issuer.handedOverAt(),
            0L,
            issuer.createdAt(),
            issuer.createdBy(),
            issuer.updatedAt(),
            issuer.updatedBy(),
            issuer.traceId());
    }

    private SigningKey withKeyDatabaseIdentity(SigningKey key, Long id) {
        return new SigningKey(
            id,
            key.tenantId(),
            key.authorityId(),
            key.issuerInstanceId(),
            key.keyId(),
            key.rootFingerprint(),
            key.certificateChainPem(),
            key.status(),
            key.notBefore(),
            key.notAfter(),
            key.authorizedFromHandoverSequence(),
            key.authorizedThroughHandoverSequence(),
            0L,
            key.createdAt(),
            key.createdBy(),
            key.updatedAt(),
            key.updatedBy(),
            key.traceId());
    }

    private static boolean looksLikePrivateMaterial(String componentName) {
        String normalized = componentName.toLowerCase(Locale.ROOT);
        return normalized.contains("privatekey")
            || normalized.contains("private_key")
            || normalized.contains("secret")
            || normalized.contains("credential")
            || normalized.equals("keymaterial")
            || normalized.equals("key_material");
    }
}
