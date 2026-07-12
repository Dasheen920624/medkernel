package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.context.ClinicalRuntimeAssetSelection;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseCommand;
import com.medkernel.engine.context.ClinicalRuntimeReleaseService;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture;
import com.medkernel.engine.knowledge.authority.VerifiedPackageSignature;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.release.PlatformUpgradeDiffSummary;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 预检确认、隔离文件重验、空状态 CAS 和网络重试必须形成一个原子激活事实。 */
class FullPackageActivationServiceTest {

    private static final String TENANT_ID = "tenant-hospital";
    private static final String HOSPITAL_ID = "hospital-A";
    private static final String PREFLIGHT_ID = "preflight-" + "1".repeat(40);
    private static final String PREVIEW_DIGEST = "sm3:" + "d".repeat(64);
    private static final Instant NOW = FullPackageTestFixture.NOW;

    private final FullPackagePreflightRepository preflights =
        mock(FullPackagePreflightRepository.class);
    private final FullPackageActivationRepository activations =
        mock(FullPackageActivationRepository.class);
    private final FullPackageQuarantineStore quarantine = mock(FullPackageQuarantineStore.class);
    private final FullPackageArchiveValidator archives = mock(FullPackageArchiveValidator.class);
    private final FullPackageTrustValidator trust = mock(FullPackageTrustValidator.class);
    private final FullPackagePreviewAnalyzer previews = mock(FullPackagePreviewAnalyzer.class);
    private final FullPackagePreflightPreviewCodec previewCodec =
        mock(FullPackagePreflightPreviewCodec.class);
    private final FullPackageMaterializer materializer = mock(FullPackageMaterializer.class);
    private final ClinicalRuntimeReleaseService runtimes =
        mock(ClinicalRuntimeReleaseService.class);
    private final OrgUnitRepository organizations = mock(OrgUnitRepository.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final FullPackageTestFixture packages = new FullPackageTestFixture();

    private FullPackageActivationService service;
    private FullPackageTestFixture.SignedPackage source;
    private QuarantinedFullPackage artifact;
    private FullPackageInspection inspection;
    private VerifiedPackageSignature verified;
    private FullPackagePreflight preflight;
    private FullPackagePreflightPreview preview;

    @BeforeEach
    void setUp() {
        source = packages.build("delivery-activation-1", 1);
        artifact = new QuarantinedFullPackage(
            Path.of("/quarantine/package.mkp"),
            "objects/cc/" + "c".repeat(64) + ".mkp",
            "sm3:" + "c".repeat(64),
            source.bytes().length);
        inspection = new FullPackageInspection(
            artifact,
            source.manifest(),
            source.envelope(),
            source.release(),
            source.documents(),
            16,
            source.bytes().length);
        verified = new VerifiedPackageSignature(
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
            NOW);
        preview = preview(PREVIEW_DIGEST);
        preflight = preflight();
        service = new FullPackageActivationService(
            preflights,
            activations,
            quarantine,
            archives,
            trust,
            previews,
            previewCodec,
            materializer,
            runtimes,
            organizations,
            audit,
            new SmCryptoService(),
            Clock.fixed(NOW, ZoneOffset.UTC));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-activation",
            new OrgScope(
                TENANT_ID, "group-A", HOSPITAL_ID, null, null, null, null, null),
            "medical-governor"));
        arrangePassedPreflight();
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void revalidatesAndAtomicallyActivatesTheConfirmedBlankStatePackage() {
        ClinicalRuntimeRelease runtime = new ClinicalRuntimeRelease(
            9L,
            "runtime-imported-1",
            TENANT_ID,
            HOSPITAL_ID,
            1L,
            source.release().platformReleaseIdentity(),
            "a".repeat(64),
            null,
            NOW,
            "medical-governor",
            NOW,
            "medical-governor",
            "trace-activation");
        when(materializer.materialize(
            inspection, verified, "medical-governor", "trace-activation", NOW))
            .thenReturn(new FullPackageMaterializationResult(
                source.release().platformReleaseIdentity(),
                1,
                List.of(ClinicalRuntimeAssetSelection.platform(
                    VersionedAssetType.KNOWLEDGE, "ASSET.KNOWLEDGE"))));
        when(runtimes.activate(any(ClinicalRuntimeReleaseCommand.class))).thenReturn(runtime);
        when(activations.save(any(FullPackageActivation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        FullPackageActivation result = service.activate(new FullPackageActivationCommand(
            HOSPITAL_ID, PREFLIGHT_ID, PREVIEW_DIGEST, null));

        assertThat(result.preflightId()).isEqualTo(PREFLIGHT_ID);
        assertThat(result.runtimeReleaseId()).isEqualTo(runtime.releaseId());
        assertThat(result.runtimeRevisionNo()).isEqualTo(1);
        ArgumentCaptor<ClinicalRuntimeReleaseCommand> command =
            ArgumentCaptor.forClass(ClinicalRuntimeReleaseCommand.class);
        verify(runtimes).activate(command.capture());
        assertThat(command.getValue().expectedCurrentReleaseId()).isNull();
        assertThat(command.getValue().activeAssets()).singleElement()
            .extracting(ClinicalRuntimeAssetSelection::assetIdentity)
            .isEqualTo("ASSET.KNOWLEDGE");
    }

    @Test
    void rejectsAnArtifactChangedAfterPreflightWithoutBusinessWrites() {
        when(quarantine.resolve(
            preflight.quarantineCoordinate(),
            preflight.packageFileDigest(),
            preflight.packageFileSize()))
            .thenThrow(new ApiException(ErrorCode.CONFLICT, "隔离文件已被替换"));

        assertThatThrownBy(() -> service.activate(new FullPackageActivationCommand(
            HOSPITAL_ID, PREFLIGHT_ID, PREVIEW_DIGEST, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("替换");

        verify(materializer, never()).materialize(any(), any(), any(), any(), any());
        verify(runtimes, never()).activate(any());
        verify(activations, never()).save(any());
    }

    @Test
    void rejectsAStalePreviewAfterTheHospitalCurrentStateChanges() {
        FullPackagePreflightPreview changed = preview("sm3:" + "e".repeat(64));
        when(previewCodec.seal(any(FullPackagePreflightPreview.class))).thenReturn(changed);

        assertThatThrownBy(() -> service.activate(new FullPackageActivationCommand(
            HOSPITAL_ID, PREFLIGHT_ID, PREVIEW_DIGEST, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("预检后医院")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(materializer, never()).materialize(any(), any(), any(), any(), any());
        verify(runtimes, never()).activate(any());
    }

    @Test
    void exactNetworkRetryReturnsTheExistingActivationWithoutReimporting() {
        FullPackageActivation existing = new FullPackageActivation(
            7L,
            "activation-existing",
            PREFLIGHT_ID,
            TENANT_ID,
            HOSPITAL_ID,
            source.manifest().authorityId(),
            source.manifest().deliveryId(),
            PREVIEW_DIGEST,
            null,
            "runtime-imported-1",
            1L,
            source.release().platformReleaseIdentity(),
            NOW,
            "medical-governor",
            NOW,
            "medical-governor",
            NOW,
            "medical-governor",
            "trace-activation");
        when(activations.findByTenantIdAndHospitalIdAndPreflightId(
            TENANT_ID, HOSPITAL_ID, PREFLIGHT_ID)).thenReturn(Optional.of(existing));

        FullPackageActivation repeated = service.activate(new FullPackageActivationCommand(
            HOSPITAL_ID, PREFLIGHT_ID, PREVIEW_DIGEST, null));

        assertThat(repeated).isSameAs(existing);
        verify(quarantine, never()).resolve(any(), any(), any(Long.class));
        verify(materializer, never()).materialize(any(), any(), any(), any(), any());
    }

    private void arrangePassedPreflight() {
        when(preflights.findByTenantIdAndHospitalIdAndPreflightIdForUpdate(
            TENANT_ID, HOSPITAL_ID, PREFLIGHT_ID)).thenReturn(Optional.of(preflight));
        when(activations.findByTenantIdAndHospitalIdAndPreflightId(
            TENANT_ID, HOSPITAL_ID, PREFLIGHT_ID)).thenReturn(Optional.empty());
        when(organizations.findByTenantIdAndIdForUpdate(TENANT_ID, HOSPITAL_ID))
            .thenReturn(Optional.of(mock(OrgUnit.class)));
        when(quarantine.resolve(
            preflight.quarantineCoordinate(),
            preflight.packageFileDigest(),
            preflight.packageFileSize())).thenReturn(artifact);
        when(archives.inspect(artifact, HOSPITAL_ID)).thenReturn(inspection);
        when(trust.verify(inspection)).thenReturn(verified);
        when(previewCodec.decode(preflight.previewJson())).thenReturn(preview);
        when(previews.analyze(
            TENANT_ID, HOSPITAL_ID, PREFLIGHT_ID, inspection, preview.createdAt()))
            .thenReturn(preview(null));
        when(previewCodec.seal(any(FullPackagePreflightPreview.class))).thenReturn(preview);
        when(previewCodec.encode(preview)).thenReturn(preflight.previewJson());
    }

    private FullPackagePreflight preflight() {
        return new FullPackagePreflight(
            1L,
            PREFLIGHT_ID,
            TENANT_ID,
            HOSPITAL_ID,
            source.manifest().authorityId(),
            source.manifest().deliveryId(),
            source.manifest().releaseSequence(),
            source.envelope().manifestDigest(),
            source.release().platformReleaseIdentity(),
            artifact.packageFileDigest(),
            artifact.packageFileSize(),
            artifact.quarantineCoordinate(),
            source.envelope().issuerInstanceId(),
            source.envelope().keyId(),
            source.envelope().rootFingerprint(),
            FullPackagePreflightStatus.PASSED,
            PREVIEW_DIGEST,
            "{\"sealed\":true}",
            0L,
            NOW,
            "medical-governor",
            NOW,
            "medical-governor",
            "trace-preflight");
    }

    private FullPackagePreflightPreview preview(String digest) {
        return new FullPackagePreflightPreview(
            "1.0",
            PREFLIGHT_ID,
            FullPackagePreflightStatus.PASSED,
            TENANT_ID,
            HOSPITAL_ID,
            false,
            source.manifest().authorityId(),
            source.manifest().deliveryId(),
            source.manifest().releaseSequence(),
            source.envelope().manifestDigest(),
            source.release().platformReleaseIdentity(),
            artifact.packageFileDigest(),
            artifact.packageFileSize(),
            artifact.quarantineCoordinate(),
            null,
            new PlatformUpgradeDiffSummary(13, 0, 1, 0, 0),
            List.of(),
            new FullPackagePreflightPreview.ImpactSummary(0, 0, 1, 0),
            source.release().withdrawals(),
            16,
            source.bytes().length,
            NOW,
            digest);
    }
}
