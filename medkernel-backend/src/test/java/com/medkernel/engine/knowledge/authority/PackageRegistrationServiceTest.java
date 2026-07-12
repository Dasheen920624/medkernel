package com.medkernel.engine.knowledge.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** 医疗资源包不可变注册、幂等和冲突拒绝合同测试。 */
class PackageRegistrationServiceTest {

    private static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    private static final String ISSUER_ID = "issuer-platform-134";
    private static final String KEY_ID = "kms:key:issuer-134";
    private static final String ROOT_FINGERPRINT = "sm3:" + "a".repeat(64);
    private static final String MANIFEST_DIGEST = "sm3:" + "b".repeat(64);
    private static final String PACKAGE_DIGEST = "sm3:" + "d".repeat(64);
    private static final long PACKAGE_SIZE = 4_096L;
    private static final String STORAGE_COORDINATE =
        "mkp-full-000001/" + "b".repeat(64) + ".mkp";
    private static final String PLATFORM_RELEASE_ID = "baseline-release-0001";
    private static final String DELIVERY_ID = "mkp-full-000001";
    private static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    private PackageRegistrationRepository registrations;
    private AuthorityRepository authorities;
    private PackageSignatureVerifier verifier;
    private AuditRecorder auditRecorder;
    private IsolatedAuditPublisher isolatedAudit;
    private PackageRegistrationService service;
    private PackageSignatureEnvelope envelope;
    private TrustedAuthorityAnchor anchor;

    @BeforeEach
    void setUp() {
        registrations = mock(PackageRegistrationRepository.class);
        authorities = mock(AuthorityRepository.class);
        verifier = mock(PackageSignatureVerifier.class);
        auditRecorder = mock(AuditRecorder.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        service = new PackageRegistrationService(
            registrations,
            authorities,
            verifier,
            auditRecorder,
            isolatedAudit,
            Clock.fixed(NOW, ZoneOffset.UTC));
        envelope = new PackageSignatureEnvelope(
            AUTHORITY_ID,
            ISSUER_ID,
            KEY_ID,
            ROOT_FINGERPRINT,
            1,
            MANIFEST_DIGEST,
            "PUBLIC-CERTIFICATE-CHAIN",
            NOW.minusSeconds(1),
            "PUBLIC-SIGNATURE");
        anchor = new TrustedAuthorityAnchor(AUTHORITY_ID, ROOT_FINGERPRINT);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-package-register", OrgScope.tenant(PlatformTenant.ID), "platform-publisher"));
        when(verifier.verify(anchor, envelope)).thenReturn(new VerifiedPackageSignature(
            AUTHORITY_ID,
            ISSUER_ID,
            KEY_ID,
            ROOT_FINGERPRINT,
            1,
            MANIFEST_DIGEST,
            envelope.certificateChainPem(),
            NOW.minusSeconds(3600),
            NOW.plusSeconds(3600),
            NOW.minusSeconds(1),
            NOW));
        when(authorities.findByTenantIdAndAuthorityId(PlatformTenant.ID, AUTHORITY_ID))
            .thenReturn(Optional.of(authority(0)));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void registersSignedFullPackageOnceAndReturnsSameFactOnRetry() {
        PackageRegistrationCommand command = new PackageRegistrationCommand(
            DELIVERY_ID,
            MedicalPackageType.FULL,
            null,
            null,
            null,
            PLATFORM_RELEASE_ID,
            PACKAGE_DIGEST,
            PACKAGE_SIZE,
            STORAGE_COORDINATE);
        when(registrations.findByTenantIdAndAuthorityIdAndDeliveryId(
            PlatformTenant.ID, AUTHORITY_ID, DELIVERY_ID))
            .thenReturn(Optional.empty());
        when(registrations.findByTenantIdAndAuthorityIdAndReleaseSequence(
            PlatformTenant.ID, AUTHORITY_ID, 1)).thenReturn(Optional.empty());
        when(registrations.save(any(PackageRegistration.class))).thenAnswer(invocation ->
            withDatabaseId(invocation.getArgument(0, PackageRegistration.class), 41L));
        when(authorities.save(any(Authority.class))).thenAnswer(invocation ->
            invocation.getArgument(0, Authority.class));

        PackageRegistration first = service.register(command, anchor, envelope);

        when(registrations.findByTenantIdAndAuthorityIdAndDeliveryId(
            PlatformTenant.ID, AUTHORITY_ID, DELIVERY_ID)).thenReturn(Optional.of(first));
        PackageRegistration retry = service.register(command, anchor, envelope);

        ArgumentCaptor<PackageRegistration> persisted =
            ArgumentCaptor.forClass(PackageRegistration.class);
        verify(registrations, times(1)).save(persisted.capture());
        verify(authorities, times(1)).save(any(Authority.class));
        verify(auditRecorder, times(1)).record(
            eq(AuditAction.PUBLISH),
            eq("mk_knowledge_package_registration"),
            eq(DELIVERY_ID),
            any(String.class));
        assertThat(persisted.getValue().tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(persisted.getValue().manifestDigest()).isEqualTo(MANIFEST_DIGEST);
        assertThat(persisted.getValue().platformReleaseIdentity()).isEqualTo(PLATFORM_RELEASE_ID);
        assertThat(persisted.getValue().packageFileDigest()).isEqualTo(PACKAGE_DIGEST);
        assertThat(persisted.getValue().packageFileSize()).isEqualTo(PACKAGE_SIZE);
        assertThat(persisted.getValue().storageCoordinate()).isEqualTo(STORAGE_COORDINATE);
        assertThat(persisted.getValue().packageType()).isEqualTo(MedicalPackageType.FULL);
        assertThat(persisted.getValue().signingStatus()).isEqualTo(PackageSigningStatus.SIGNED);
        assertThat(persisted.getValue().traceId()).isEqualTo("trace-package-register");
        assertThat(first.id()).isEqualTo(41L);
        assertThat(retry).isSameAs(first);
    }

    @Test
    void rejectsSameDeliveryIdWithDifferentManifestDigest() {
        PackageRegistration existing = registered(
            41L, DELIVERY_ID, 1, MANIFEST_DIGEST, NOW.minusSeconds(1));
        String alteredDigest = "sm3:" + "c".repeat(64);
        PackageSignatureEnvelope alteredEnvelope = new PackageSignatureEnvelope(
            AUTHORITY_ID,
            ISSUER_ID,
            KEY_ID,
            ROOT_FINGERPRINT,
            1,
            alteredDigest,
            "PUBLIC-CERTIFICATE-CHAIN",
            NOW.minusSeconds(1),
            "PUBLIC-SIGNATURE");
        when(verifier.verify(anchor, alteredEnvelope)).thenReturn(new VerifiedPackageSignature(
            AUTHORITY_ID,
            ISSUER_ID,
            KEY_ID,
            ROOT_FINGERPRINT,
            1,
            alteredDigest,
            alteredEnvelope.certificateChainPem(),
            NOW.minusSeconds(3600),
            NOW.plusSeconds(3600),
            NOW.minusSeconds(1),
            NOW));
        when(registrations.findByTenantIdAndAuthorityIdAndDeliveryId(
            PlatformTenant.ID, AUTHORITY_ID, DELIVERY_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(
            new PackageRegistrationCommand(
                DELIVERY_ID,
                MedicalPackageType.FULL,
                null,
                null,
                null,
                PLATFORM_RELEASE_ID,
                PACKAGE_DIGEST,
                PACKAGE_SIZE,
                DELIVERY_ID + "/" + "c".repeat(64) + ".mkp"),
            anchor,
            alteredEnvelope))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(registrations, never()).save(any());
        verify(authorities, never()).save(any());
        assertFailureAudit(ErrorCode.CONFLICT, DELIVERY_ID);
    }

    @Test
    void rejectsDifferentDeliveryThatReusesReleaseSequence() {
        String otherDeliveryId = "mkp-full-000001-other";
        when(registrations.findByTenantIdAndAuthorityIdAndDeliveryId(
            PlatformTenant.ID, AUTHORITY_ID, otherDeliveryId)).thenReturn(Optional.empty());
        when(registrations.findByTenantIdAndAuthorityIdAndReleaseSequence(
            PlatformTenant.ID, AUTHORITY_ID, 1)).thenReturn(Optional.of(registered(
                41L, DELIVERY_ID, 1, MANIFEST_DIGEST, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.register(
            fullCommand(otherDeliveryId),
            anchor,
            envelope))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(registrations, never()).save(any());
        verify(authorities, never()).save(any());
        assertFailureAudit(ErrorCode.CONFLICT, otherDeliveryId);
    }

    @Test
    void rejectsDeltaPackageFromFirstLaunchRegistry() {
        PackageRegistrationCommand delta = new PackageRegistrationCommand(
            "mkp-delta-000001",
            MedicalPackageType.DELTA,
            DELIVERY_ID,
            MANIFEST_DIGEST,
            MANIFEST_DIGEST,
            PLATFORM_RELEASE_ID,
            PACKAGE_DIGEST,
            PACKAGE_SIZE,
            STORAGE_COORDINATE);

        assertThatThrownBy(() -> service.register(delta, anchor, envelope))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED));

        verifyNoInteractions(verifier);
        verify(registrations, never()).save(any());
        verify(authorities, never()).save(any());
        assertFailureAudit(ErrorCode.VALIDATION_FAILED, delta.deliveryId());
    }

    private Authority authority(long releaseSequence) {
        return new Authority(
            1L,
            PlatformTenant.ID,
            AUTHORITY_ID,
            ISSUER_ID,
            ROOT_FINGERPRINT,
            0,
            releaseSequence,
            0L,
            NOW.minusSeconds(60),
            "bootstrap",
            NOW.minusSeconds(60),
            "bootstrap",
            "trace-authority");
    }

    private PackageRegistration withDatabaseId(PackageRegistration registration, long id) {
        return new PackageRegistration(
            id,
            registration.tenantId(),
            registration.authorityId(),
            registration.deliveryId(),
            registration.releaseSequence(),
            registration.manifestDigest(),
            registration.platformReleaseIdentity(),
            registration.packageFileDigest(),
            registration.packageFileSize(),
            registration.storageCoordinate(),
            registration.issuerInstanceId(),
            registration.keyId(),
            registration.parentDeliveryId(),
            registration.parentManifestDigest(),
            registration.baseManifestDigest(),
            registration.packageType(),
            registration.signingStatus(),
            registration.signedAt(),
            registration.registeredAt(),
            0L,
            registration.createdAt(),
            registration.createdBy(),
            registration.updatedAt(),
            registration.updatedBy(),
            registration.traceId());
    }

    private PackageRegistration registered(long id,
                                           String deliveryId,
                                           long releaseSequence,
                                           String manifestDigest,
                                           Instant signedAt) {
        return new PackageRegistration(
            id,
            PlatformTenant.ID,
            AUTHORITY_ID,
            deliveryId,
            releaseSequence,
            manifestDigest,
            PLATFORM_RELEASE_ID,
            PACKAGE_DIGEST,
            PACKAGE_SIZE,
            STORAGE_COORDINATE,
            ISSUER_ID,
            KEY_ID,
            null,
            null,
            null,
            MedicalPackageType.FULL,
            PackageSigningStatus.SIGNED,
            signedAt,
            NOW,
            0L,
            NOW,
            "platform-publisher",
            NOW,
            "platform-publisher",
            "trace-package-register");
    }

    private PackageRegistrationCommand fullCommand(String deliveryId) {
        return new PackageRegistrationCommand(
            deliveryId,
            MedicalPackageType.FULL,
            null,
            null,
            null,
            PLATFORM_RELEASE_ID,
            PACKAGE_DIGEST,
            PACKAGE_SIZE,
            deliveryId + "/" + "b".repeat(64) + ".mkp");
    }

    private void assertFailureAudit(ErrorCode errorCode, String deliveryId) {
        ArgumentCaptor<AuditEvent> failure = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(failure.capture());
        assertThat(failure.getValue().action()).isEqualTo(AuditAction.PUBLISH);
        assertThat(failure.getValue().resourceType())
            .isEqualTo("mk_knowledge_package_registration");
        assertThat(failure.getValue().resourceId()).isEqualTo(deliveryId);
        assertThat(failure.getValue().errorCode()).isEqualTo(errorCode.code());
    }
}
