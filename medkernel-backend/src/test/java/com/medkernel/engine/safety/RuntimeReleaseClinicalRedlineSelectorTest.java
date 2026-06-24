package com.medkernel.engine.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.PlatformTenant;

class RuntimeReleaseClinicalRedlineSelectorTest {

    private static final Instant NOW = Instant.parse("2026-06-23T09:00:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final AssetVersionRepository assetVersions = mock(AssetVersionRepository.class);
    private final ClinicalRedlineRepository redlines = mock(ClinicalRedlineRepository.class);
    private final RuntimeReleaseClinicalRedlineSelector selector =
        new RuntimeReleaseClinicalRedlineSelector(runtime, assetVersions, redlines);

    @Test
    void selectsOnlySafetyRedlinesPinnedByTheHospitalRuntimeRelease() {
        ClinicalRuntimeReleaseItem enabledPlatform = safetyItem(
            PlatformTenant.ID, "SAFETY.RDL-DDI-001", "av-platform-safety", ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem disabledTenant = safetyItem(
            "tenant-A", "SAFETY.RDL-LOCAL-001", "av-local-safety", ReleaseEntryState.DISABLED);
        when(runtime.resolve("tenant-A", "runtime-H9")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(enabledPlatform, disabledTenant)));
        when(assetVersions.findByVersionIdAndTenantId("av-platform-safety", PlatformTenant.ID))
            .thenReturn(Optional.of(assetVersion(
                PlatformTenant.ID, "SAFETY.RDL-DDI-001", "av-platform-safety",
                "clinical-redline:platform-redline-ddi:2026.2")));
        when(redlines.findByTenantIdAndRedlineId(PlatformTenant.ID, "platform-redline-ddi"))
            .thenReturn(Optional.of(redline(PlatformTenant.ID, "platform-redline-ddi", "RDL-DDI-001")));
        when(redlines.findByTenantIdAndRedlineId("tenant-A", "local-redline"))
            .thenReturn(Optional.of(redline("tenant-A", "local-redline", "RDL-LOCAL-001")));

        List<ClinicalRedlineRule> selected = selector.select("tenant-A", "runtime-H9");

        assertThat(selected)
            .extracting(ClinicalRedlineRule::tenantId, ClinicalRedlineRule::redlineId,
                ClinicalRedlineRule::redlineKey)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(
                PlatformTenant.ID, "platform-redline-ddi", "RDL-DDI-001"));
    }

    @Test
    void rejectsRuntimeItemWhenAssetVersionSourceDoesNotResolveToThePinnedRedline() {
        ClinicalRuntimeReleaseItem enabled = safetyItem(
            PlatformTenant.ID, "SAFETY.RDL-DDI-001", "av-platform-safety", ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "runtime-H9")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(enabled)));
        when(assetVersions.findByVersionIdAndTenantId("av-platform-safety", PlatformTenant.ID))
            .thenReturn(Optional.of(assetVersion(
                PlatformTenant.ID, "SAFETY.RDL-DDI-001", "av-platform-safety",
                "legacy-redline:platform-redline-ddi")));

        assertThatThrownBy(() -> selector.select("tenant-A", "runtime-H9"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("安全红线资产来源无效");
    }

    private ClinicalRuntimeRelease release() {
        return new ClinicalRuntimeRelease(
            9L, "runtime-H9", "tenant-A", "hospital-A", 9L,
            "baseline-A13", "a".repeat(64), null,
            NOW, "tester", NOW, "tester", "trace-safety-runtime");
    }

    private ClinicalRuntimeReleaseItem safetyItem(
            String sourceTenantId,
            String assetIdentity,
            String versionId,
            ReleaseEntryState state) {
        return new ClinicalRuntimeReleaseItem(
            1L,
            "runtime-H9",
            sourceTenantId,
            sourceTenantId.equals(PlatformTenant.ID) ? ReleaseSourceLayer.PLATFORM : ReleaseSourceLayer.HOSPITAL,
            VersionedAssetType.SAFETY,
            assetIdentity,
            state,
            versionId,
            "V1",
            "b".repeat(64),
            NOW,
            "tester",
            "trace-safety-runtime"
        );
    }

    private AssetVersion assetVersion(
            String tenantId,
            String assetIdentity,
            String versionId,
            String sourceRef) {
        return new AssetVersion(
            null,
            versionId,
            tenantId,
            VersionedAssetType.SAFETY,
            assetIdentity,
            "V1",
            "ALL",
            "ALL",
            "b".repeat(64),
            AssetVersionSafetyPolicy.SAFETY_REDLINE,
            AssetVersionOverridePolicy.LOCKED,
            AssetVersionStatus.PUBLISHED,
            "version:" + versionId,
            sourceRef,
            NOW,
            null,
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-safety-runtime"
        );
    }

    private ClinicalRedlineRule redline(String tenantId, String redlineId, String redlineKey) {
        return new ClinicalRedlineRule(
            null,
            redlineId,
            tenantId,
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "order-sign",
            "TENANT",
            tenantId,
            tenantId + "|DRUG_INTERACTION|order-sign|" + redlineKey,
            redlineKey,
            "2026.2",
            ClinicalRedlineStatus.ACTIVE,
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-critical-ddi",
            "4",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "华法林合并非甾体抗炎药出血风险",
            "合用可能显著增加出血风险",
            """
            {"when":{"fact":"patient.gender","operator":"equals","value":"FEMALE"},"then":[],"explain":{"summary":"红线命中"}}
            """,
            "药品说明书与临床指南证据",
            "source-version:42#section-1",
            42L,
            false,
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-redline");
    }
}
