package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItemRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture;
import com.medkernel.engine.knowledge.authority.FullPackageTestFixture.SignedPackage;
import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideMode;
import com.medkernel.engine.versioning.InheritanceOverrideRepository;
import com.medkernel.engine.versioning.InheritanceOverrideStatus;
import com.medkernel.engine.versioning.InheritancePropagation;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 预检差异必须完全来自真实包和目标医院当前生效事实，不得创建运行时状态。 */
class FullPackagePreviewAnalyzerTest {

    private static final String TENANT_ID = "tenant-hospital-a";
    private static final String HOSPITAL_ID = "hospital-A";
    private static final String HOSPITAL_PATH = "/tenant-hospital-a/hospital-A";
    private static final Instant NOW = FullPackageTestFixture.NOW;

    private final FullPackageTestFixture packages = new FullPackageTestFixture();
    private final SignedPackage source = packages.build("mkp-full-000001", 1);
    private final ClinicalRuntimeReleaseRepository runtimes =
        mock(ClinicalRuntimeReleaseRepository.class);
    private final ClinicalRuntimeReleaseItemRepository runtimeItems =
        mock(ClinicalRuntimeReleaseItemRepository.class);
    private final OrgUnitRepository organizations = mock(OrgUnitRepository.class);
    private final InheritanceOverrideRepository overrides =
        mock(InheritanceOverrideRepository.class);
    private final FullPackagePreviewAnalyzer analyzer = new FullPackagePreviewAnalyzer(
        runtimes, runtimeItems, organizations, overrides);

    @BeforeEach
    void arrangeHospital() {
        when(organizations.findByTenantIdAndId(TENANT_ID, HOSPITAL_ID))
            .thenReturn(Optional.of(hospital()));
    }

    @Test
    void emptyHospitalShowsCompleteAdditivePreviewWithoutRuntimeMutation() {
        when(runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            TENANT_ID, HOSPITAL_ID)).thenReturn(Optional.empty());

        FullPackagePreflightPreview result = analyzer.analyze(
            TENANT_ID, HOSPITAL_ID, "preflight-empty", inspection(), NOW);

        assertThat(result.runtimeMutation()).isFalse();
        assertThat(result.currentRuntime()).isNull();
        assertThat(result.diffSummary().added()).isEqualTo(13);
        assertThat(result.diffSummary().disabled()).isEqualTo(1);
        assertThat(result.diffSummary().modified()).isZero();
        assertThat(result.diffSummary().unchanged()).isZero();
        assertThat(result.diffSummary().conflictCount()).isZero();
        assertThat(result.impactSummary().withdrawalCount()).isEqualTo(1);
        assertThat(result.impactSummary().activeWithdrawalImpactCount()).isZero();
        verifyNoInteractions(runtimeItems);
    }

    @Test
    void currentRuntimeLocalOverrideAndWithdrawalAreReflectedInImmutablePreview() {
        ClinicalRuntimeRelease runtime = runtime();
        when(runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
            TENANT_ID, HOSPITAL_ID)).thenReturn(Optional.of(runtime));
        when(runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
            runtime.releaseId())).thenReturn(List.of(
                runtimeItem(
                    VersionedAssetType.RULE,
                    "ASSET.RULE",
                    "version-rule-1",
                    sourceContentHash(VersionedAssetType.RULE, "ASSET.RULE"),
                    ReleaseEntryState.ACTIVE),
                runtimeItem(
                    VersionedAssetType.KNOWLEDGE,
                    "ASSET.KNOWLEDGE",
                    "version-knowledge-old",
                    "d".repeat(64),
                    ReleaseEntryState.ACTIVE),
                runtimeItem(
                    VersionedAssetType.KNOWLEDGE,
                    "ASSET.RETIRED",
                    "version-retired-1",
                    "e".repeat(64),
                    ReleaseEntryState.ACTIVE)));
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndLifecycleStatus(
            TENANT_ID,
            VersionedAssetType.KNOWLEDGE,
            "ASSET.KNOWLEDGE",
            InheritanceOverrideStatus.ACTIVE))
            .thenReturn(List.of(localOverride()));

        FullPackagePreflightPreview result = analyzer.analyze(
            TENANT_ID, HOSPITAL_ID, "preflight-upgrade", inspection(), NOW);

        assertThat(result.currentRuntime().releaseId()).isEqualTo(runtime.releaseId());
        assertThat(result.diffSummary().unchanged()).isEqualTo(1);
        assertThat(result.diffSummary().modified()).isEqualTo(1);
        assertThat(result.diffSummary().disabled()).isEqualTo(1);
        assertThat(result.diffSummary().added()).isEqualTo(11);
        assertThat(result.diffSummary().conflictCount()).isEqualTo(1);
        assertThat(result.differences())
            .filteredOn(item -> item.assetType() == VersionedAssetType.KNOWLEDGE
                && item.assetIdentity().equals("ASSET.KNOWLEDGE"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.changeType()).isEqualTo("MODIFIED");
                assertThat(item.conflicts()).singleElement()
                    .satisfies(conflict -> assertThat(conflict.overrideId())
                        .isEqualTo("override-knowledge-local"));
            });
        assertThat(result.impactSummary().activeWithdrawalImpactCount()).isEqualTo(1);
        assertThat(result.runtimeMutation()).isFalse();
    }

    private FullPackageInspection inspection() {
        QuarantinedFullPackage artifact = new QuarantinedFullPackage(
            Path.of("/quarantine/objects/aa/package.mkp"),
            "objects/aa/" + "c".repeat(64) + ".mkp",
            "sm3:" + "c".repeat(64),
            source.bytes().length);
        return new FullPackageInspection(
            artifact,
            source.manifest(),
            source.envelope(),
            source.release(),
            source.documents(),
            16,
            source.bytes().length);
    }

    private OrgUnit hospital() {
        return new OrgUnit(
            HOSPITAL_ID,
            null,
            TENANT_ID,
            HOSPITAL_PATH,
            OrgLevel.FACILITY,
            HOSPITAL_ID,
            "中心医院",
            null,
            OrgFacilityType.HOSPITAL,
            null,
            OrgUnitStatus.ACTIVE,
            NOW,
            "operator",
            NOW,
            "operator");
    }

    private ClinicalRuntimeRelease runtime() {
        return new ClinicalRuntimeRelease(
            9L,
            "runtime-H9",
            TENANT_ID,
            HOSPITAL_ID,
            9L,
            "baseline-A8",
            "sha256:" + "f".repeat(64),
            null,
            NOW,
            "operator",
            NOW,
            "operator",
            "trace-runtime");
    }

    private ClinicalRuntimeReleaseItem runtimeItem(
            VersionedAssetType type,
            String identity,
            String versionId,
            String contentHash,
            ReleaseEntryState state) {
        return new ClinicalRuntimeReleaseItem(
            null,
            "runtime-H9",
            TENANT_ID,
            ReleaseSourceLayer.PLATFORM,
            type,
            identity,
            state,
            versionId,
            "V1",
            contentHash,
            NOW,
            "operator",
            "trace-runtime");
    }

    private InheritanceOverride localOverride() {
        return new InheritanceOverride(
            1L,
            "override-knowledge-local",
            TENANT_ID,
            VersionedAssetType.KNOWLEDGE,
            "ASSET.KNOWLEDGE",
            "version-knowledge-old",
            "version-knowledge-local",
            InheritanceOverrideMode.REPLACE,
            InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.ACTIVE,
            HOSPITAL_PATH,
            "adult|inpatient",
            "本院覆盖",
            "本院制度",
            "医务处",
            NOW,
            "operator",
            NOW,
            "operator",
            "trace-override");
    }

    private String sourceContentHash(VersionedAssetType type, String identity) {
        String digest = source.release().entries().stream()
            .filter(entry -> entry.assetType() == type && entry.assetIdentity().equals(identity))
            .findFirst()
            .orElseThrow()
            .sourceContentSha256();
        return digest.substring("sha256:".length());
    }
}
