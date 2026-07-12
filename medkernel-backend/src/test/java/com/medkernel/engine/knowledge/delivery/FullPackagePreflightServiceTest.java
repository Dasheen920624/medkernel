package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture.SignedPackage;
import com.medkernel.engine.knowledge.authority.PackageRegistration;
import com.medkernel.engine.knowledge.authority.PackageRegistrationRepository;
import com.medkernel.engine.knowledge.authority.VerifiedPackageSignature;
import com.medkernel.engine.release.PlatformUpgradeDiffSummary;
import com.medkernel.engine.release.PlatformUpgradeRuntimeSnapshot;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.relational.core.conversion.DbAction;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;

/** 真实包预检只写不可变预检账本，失败和重放不得触碰包注册或业务数据。 */
class FullPackagePreflightServiceTest {

    private static final String TENANT_ID = "tenant-hospital-a";
    private static final String HOSPITAL_ID = "hospital-A";
    private static final Instant NOW = FullPackageTestFixture.NOW;

    private final FullPackageTestFixture packages = new FullPackageTestFixture();
    private final SignedPackage source = packages.build("mkp-full-000001", 1);
    private final QuarantinedFullPackage artifact = new QuarantinedFullPackage(
        Path.of("/quarantine/objects/aa/package.mkp"),
        "objects/aa/" + "c".repeat(64) + ".mkp",
        "sm3:" + "c".repeat(64),
        source.bytes().length);
    private final FullPackageInspection inspection = new FullPackageInspection(
        artifact,
        source.manifest(),
        source.envelope(),
        source.release(),
        source.documents(),
        16,
        source.bytes().length);

    private FullPackageQuarantineStore quarantine;
    private FullPackageArchiveValidator archives;
    private FullPackageTrustValidator trust;
    private FullPackagePreflightRepository preflights;
    private PackageRegistrationRepository registrations;
    private FullPackagePreviewAnalyzer previews;
    private AuditRecorder audit;
    private IsolatedAuditPublisher isolatedAudit;
    private FullPackagePreflightPreviewCodec previewCodec;
    private FullPackagePreflightService service;

    @BeforeEach
    void setUp() {
        quarantine = mock(FullPackageQuarantineStore.class);
        archives = mock(FullPackageArchiveValidator.class);
        trust = mock(FullPackageTrustValidator.class);
        preflights = mock(FullPackagePreflightRepository.class);
        registrations = mock(PackageRegistrationRepository.class);
        previews = mock(FullPackagePreviewAnalyzer.class);
        audit = mock(AuditRecorder.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        previewCodec = new FullPackagePreflightPreviewCodec(
            new ObjectMapper().findAndRegisterModules(), new SmCryptoService());
        service = new FullPackagePreflightService(
            quarantine,
            archives,
            trust,
            preflights,
            registrations,
            previews,
            previewCodec,
            audit,
            isolatedAudit,
            Clock.fixed(NOW, ZoneOffset.UTC));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-preflight",
            new OrgScope(TENANT_ID, null, HOSPITAL_ID, null, null, null, null, null),
            "hospital-release-manager"));
        arrangeValidPipeline();
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void persistsOneImmutableManifestBoundPreviewAndReturnsIt() {
        when(previews.analyze(
            org.mockito.ArgumentMatchers.eq(TENANT_ID),
            org.mockito.ArgumentMatchers.eq(HOSPITAL_ID),
            anyString(),
            org.mockito.ArgumentMatchers.eq(inspection),
            org.mockito.ArgumentMatchers.eq(NOW)))
            .thenAnswer(invocation -> draftPreview(invocation.getArgument(2, String.class)));
        when(preflights.save(any(FullPackagePreflight.class)))
            .thenAnswer(invocation -> withId(invocation.getArgument(0), 19L));

        FullPackagePreflightPreview result = service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID);

        ArgumentCaptor<FullPackagePreflight> saved =
            ArgumentCaptor.forClass(FullPackagePreflight.class);
        verify(preflights).save(saved.capture());
        assertThat(result.status()).isEqualTo(FullPackagePreflightStatus.PASSED);
        assertThat(result.runtimeMutation()).isFalse();
        assertThat(result.manifestDigest()).isEqualTo(source.envelope().manifestDigest());
        assertThat(result.previewDigest()).matches("sm3:[0-9a-f]{64}");
        assertThat(saved.getValue().previewDigest()).isEqualTo(result.previewDigest());
        assertThat(previewCodec.decode(saved.getValue().previewJson())).isEqualTo(result);
        verify(registrations, never()).save(any());
        verify(audit).record(
            org.mockito.ArgumentMatchers.eq(com.medkernel.shared.audit.AuditAction.IMPORT),
            org.mockito.ArgumentMatchers.eq("mk_knowledge_package_preflight"),
            org.mockito.ArgumentMatchers.eq(result.preflightId()),
            anyString());
    }

    @Test
    void exactRepeatRechecksCurrentStateAndReturnsExistingImmutablePreview() {
        when(previews.analyze(
            org.mockito.ArgumentMatchers.eq(TENANT_ID),
            org.mockito.ArgumentMatchers.eq(HOSPITAL_ID),
            anyString(),
            org.mockito.ArgumentMatchers.eq(inspection),
            org.mockito.ArgumentMatchers.eq(NOW)))
            .thenAnswer(invocation -> draftPreview(invocation.getArgument(2, String.class)));
        when(preflights.save(any(FullPackagePreflight.class)))
            .thenAnswer(invocation -> withId(invocation.getArgument(0), 19L));
        FullPackagePreflightPreview first = service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID);
        ArgumentCaptor<FullPackagePreflight> saved =
            ArgumentCaptor.forClass(FullPackagePreflight.class);
        verify(preflights).save(saved.capture());
        when(preflights.findByTenantIdAndHospitalIdAndPreflightId(
            TENANT_ID,
            HOSPITAL_ID,
            first.preflightId()))
            .thenReturn(Optional.of(saved.getValue()));

        FullPackagePreflightPreview repeated = service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID);

        assertThat(repeated).isEqualTo(first);
        verify(preflights, times(1)).save(any());
        verify(previews, times(2)).analyze(
            anyString(), anyString(), anyString(), any(), any());
        verify(registrations, never()).save(any());
    }

    @Test
    void samePackageAfterHospitalRuntimeChangedCreatesANewImmutablePreview() {
        when(previews.analyze(
            org.mockito.ArgumentMatchers.eq(TENANT_ID),
            org.mockito.ArgumentMatchers.eq(HOSPITAL_ID),
            anyString(),
            org.mockito.ArgumentMatchers.eq(inspection),
            org.mockito.ArgumentMatchers.eq(NOW)))
            .thenAnswer(invocation -> draftPreview(invocation.getArgument(2, String.class)));
        when(preflights.save(any(FullPackagePreflight.class)))
            .thenAnswer(invocation -> withId(invocation.getArgument(0), 19L));
        FullPackagePreflightPreview first = service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID);
        ArgumentCaptor<FullPackagePreflight> saved =
            ArgumentCaptor.forClass(FullPackagePreflight.class);
        verify(preflights).save(saved.capture());
        when(preflights.findByTenantIdAndHospitalIdAndPreflightId(
            TENANT_ID,
            HOSPITAL_ID,
            first.preflightId()))
            .thenReturn(Optional.of(saved.getValue()));
        PlatformUpgradeRuntimeSnapshot changedRuntime = new PlatformUpgradeRuntimeSnapshot(
            "runtime-release-2",
            2,
            "platform-release-before-import",
            "a".repeat(64));
        when(previews.analyze(
            org.mockito.ArgumentMatchers.eq(TENANT_ID),
            org.mockito.ArgumentMatchers.eq(HOSPITAL_ID),
            anyString(),
            org.mockito.ArgumentMatchers.eq(inspection),
            org.mockito.ArgumentMatchers.eq(NOW)))
            .thenAnswer(invocation -> draftPreview(
                invocation.getArgument(2, String.class), changedRuntime));

        FullPackagePreflightPreview repeated = service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID);

        assertThat(repeated.preflightId()).isNotEqualTo(first.preflightId());
        assertThat(repeated.currentRuntime()).isEqualTo(changedRuntime);
        verify(preflights, times(2)).save(any());
        verify(previews, times(2)).analyze(
            anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void replayCursorUsesMaterializedRegistrationRatherThanPreflightHistory() {
        when(previews.analyze(
            org.mockito.ArgumentMatchers.eq(TENANT_ID),
            org.mockito.ArgumentMatchers.eq(HOSPITAL_ID),
            anyString(),
            org.mockito.ArgumentMatchers.eq(inspection),
            org.mockito.ArgumentMatchers.eq(NOW)))
            .thenAnswer(invocation -> draftPreview(invocation.getArgument(2, String.class)));
        when(preflights.save(any(FullPackagePreflight.class)))
            .thenAnswer(invocation -> withId(invocation.getArgument(0), 19L));

        FullPackagePreflightPreview result = service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID);

        assertThat(result.status()).isEqualTo(FullPackagePreflightStatus.PASSED);
        verify(preflights)
            .findByTenantIdAndHospitalIdAndAuthorityIdAndReleaseSequenceOrderByCreatedAtDesc(
                TENANT_ID,
                HOSPITAL_ID,
                source.manifest().authorityId(),
                source.manifest().releaseSequence());
        verify(registrations).findByTenantIdAndAuthorityIdOrderByReleaseSequenceDesc(
            com.medkernel.shared.context.PlatformTenant.ID,
            source.manifest().authorityId());
        verify(preflights).save(any());
        verify(previews).analyze(anyString(), anyString(), anyString(), any(), any());
        verify(registrations, never()).save(any());
    }

    @Test
    void rejectsSameSequenceWithDifferentDigestBeforePreviewWrite() {
        FullPackagePreflight fork = mock(FullPackagePreflight.class);
        when(fork.deliveryId()).thenReturn("mkp-full-fork");
        when(fork.manifestDigest()).thenReturn("sm3:" + "d".repeat(64));
        when(preflights
            .findByTenantIdAndHospitalIdAndAuthorityIdAndReleaseSequenceOrderByCreatedAtDesc(
            TENANT_ID, HOSPITAL_ID, source.manifest().authorityId(), 1))
            .thenReturn(List.of(fork));

        assertConflict(() -> service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID), "同序号");
        verify(preflights, never()).save(any());
        verify(previews, never()).analyze(anyString(), anyString(), anyString(), any(), any());
        verify(registrations, never()).save(any());
    }

    @Test
    void rejectsSequenceOlderThanMaterializedRegistration() {
        PackageRegistration accepted = mock(PackageRegistration.class);
        when(accepted.releaseSequence()).thenReturn(2L);
        when(accepted.deliveryId()).thenReturn("mkp-full-accepted-000002");
        when(registrations.findByTenantIdAndAuthorityIdOrderByReleaseSequenceDesc(
            com.medkernel.shared.context.PlatformTenant.ID,
            source.manifest().authorityId()))
            .thenReturn(List.of(accepted));

        assertConflict(() -> service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID), "本地包注册账本之前");
        verify(preflights, never()).save(any());
        verify(previews, never()).analyze(anyString(), anyString(), anyString(), any(), any());
        verify(registrations, never()).save(any());
    }

    @Test
    void invalidArchiveWritesOnlyFailureAuditAndNoPreflightOrPackageRegistration() {
        when(archives.inspect(artifact, HOSPITAL_ID))
            .thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED, "医疗资源包条目摘要被篡改"));

        assertThatThrownBy(() -> service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(trust, never()).verify(any());
        verify(preflights, never()).save(any());
        verify(registrations, never()).save(any());
        verify(audit, never()).record(
            any(), anyString(), anyString(), anyString());
        verify(isolatedAudit).publishInNewTx(any());
    }

    @Test
    void jdbcWrappedUniqueConflictRecoversExactConcurrentPreflight() {
        when(previews.analyze(
            org.mockito.ArgumentMatchers.eq(TENANT_ID),
            org.mockito.ArgumentMatchers.eq(HOSPITAL_ID),
            anyString(),
            org.mockito.ArgumentMatchers.eq(inspection),
            org.mockito.ArgumentMatchers.eq(NOW)))
            .thenAnswer(invocation -> draftPreview(invocation.getArgument(2, String.class)));
        when(preflights.save(any(FullPackagePreflight.class))).thenAnswer(invocation -> {
            FullPackagePreflight attempted = invocation.getArgument(0);
            when(preflights.findByTenantIdAndHospitalIdAndPreflightId(
                TENANT_ID,
                HOSPITAL_ID,
                attempted.preflightId()))
                .thenReturn(Optional.of(withId(attempted, 20L)));
            throw new DbActionExecutionException(
                mock(DbAction.class),
                new DataIntegrityViolationException("并发唯一约束冲突"));
        });

        FullPackagePreflightPreview result = service.preflight(
            new ByteArrayInputStream(source.bytes()), HOSPITAL_ID);

        assertThat(result.status()).isEqualTo(FullPackagePreflightStatus.PASSED);
        assertThat(result.manifestDigest()).isEqualTo(source.envelope().manifestDigest());
        verify(preflights).save(any());
        verify(registrations, never()).save(any());
    }

    private void arrangeValidPipeline() {
        when(quarantine.ingest(any())).thenReturn(artifact);
        when(archives.inspect(artifact, HOSPITAL_ID)).thenReturn(inspection);
        when(trust.verify(inspection)).thenReturn(new VerifiedPackageSignature(
            source.envelope().authorityId(),
            source.envelope().issuerInstanceId(),
            source.envelope().keyId(),
            source.envelope().rootFingerprint(),
            source.envelope().releaseSequence(),
            source.envelope().manifestDigest(),
            source.envelope().certificateChainPem(),
            NOW.minusSeconds(3600),
            NOW.plusSeconds(3600),
            source.envelope().signedAt(),
            NOW));
        when(preflights
            .findByTenantIdAndHospitalIdAndAuthorityIdAndReleaseSequenceOrderByCreatedAtDesc(
            TENANT_ID,
            HOSPITAL_ID,
            source.manifest().authorityId(),
            source.manifest().releaseSequence()))
            .thenReturn(List.of());
        when(registrations.findByTenantIdAndAuthorityIdOrderByReleaseSequenceDesc(
            com.medkernel.shared.context.PlatformTenant.ID,
            source.manifest().authorityId()))
            .thenReturn(List.of());
    }

    private FullPackagePreflightPreview draftPreview(String preflightId) {
        return draftPreview(preflightId, null);
    }

    private FullPackagePreflightPreview draftPreview(
            String preflightId,
            PlatformUpgradeRuntimeSnapshot currentRuntime) {
        return new FullPackagePreflightPreview(
            "1.0",
            preflightId,
            FullPackagePreflightStatus.PASSED,
            TENANT_ID,
            HOSPITAL_ID,
            false,
            source.manifest().authorityId(),
            source.manifest().deliveryId(),
            source.manifest().releaseSequence(),
            source.envelope().manifestDigest(),
            source.manifest().platformReleaseIdentity(),
            artifact.packageFileDigest(),
            artifact.packageFileSize(),
            artifact.quarantineCoordinate(),
            currentRuntime,
            new PlatformUpgradeDiffSummary(13, 0, 1, 0, 0),
            List.of(),
            new FullPackagePreflightPreview.ImpactSummary(0, 0, 1, 0),
            source.release().withdrawals(),
            inspection.archiveEntryCount(),
            inspection.expandedBytes(),
            NOW,
            null);
    }

    private FullPackagePreflight withId(FullPackagePreflight value, long id) {
        return new FullPackagePreflight(
            id,
            value.preflightId(),
            value.tenantId(),
            value.hospitalId(),
            value.authorityId(),
            value.deliveryId(),
            value.releaseSequence(),
            value.manifestDigest(),
            value.platformReleaseIdentity(),
            value.packageFileDigest(),
            value.packageFileSize(),
            value.quarantineCoordinate(),
            value.issuerInstanceId(),
            value.keyId(),
            value.rootFingerprint(),
            value.status(),
            value.previewDigest(),
            value.previewJson(),
            value.lockVersion(),
            value.createdAt(),
            value.createdBy(),
            value.updatedAt(),
            value.updatedBy(),
            value.traceId());
    }

    private void assertConflict(Runnable invocation, String message) {
        assertThatThrownBy(invocation::run)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                assertThat(exception).hasMessageContaining(message);
            });
    }
}
