package com.medkernel.engine.cdss.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

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

class RuntimeReleaseCdssRiskMatrixSelectorTest {

    private static final Instant NOW = Instant.parse("2026-06-23T10:00:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final AssetVersionRepository assetVersions = mock(AssetVersionRepository.class);
    private final CdssRiskMatrixRepository matrices = mock(CdssRiskMatrixRepository.class);
    private final RuntimeReleaseCdssRiskMatrixSelector selector =
        new RuntimeReleaseCdssRiskMatrixSelector(runtime, assetVersions, matrices);

    @Test
    void selectsTheRiskMatrixRulePinnedByTheHospitalRuntimeRelease() {
        ClinicalRuntimeReleaseItem activeMatrix = matrixItem(
            PlatformTenant.ID, "av-cdss-risk-v4", ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "runtime-H9")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(activeMatrix)));
        when(assetVersions.findByVersionIdAndTenantId("av-cdss-risk-v4", PlatformTenant.ID))
            .thenReturn(Optional.of(assetVersion(PlatformTenant.ID, "av-cdss-risk-v4", "cdss-risk-matrix:4")));
        CdssRiskMatrixRule lowRule = rule(
            PlatformTenant.ID, "matrix-v4-low", RecommendationRiskLevel.LOW,
            CdssAutomationLevel.INFORM_ONLY, RecommendationRiskLevel.LOW);
        CdssRiskMatrixRule targetRule = rule(
            PlatformTenant.ID, "matrix-v4-high", RecommendationRiskLevel.HIGH,
            CdssAutomationLevel.INTERRUPTIVE, RecommendationRiskLevel.HIGH);
        when(matrices.findByTenantIdAndMatrixVersionOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                PlatformTenant.ID, "4"))
            .thenReturn(List.of(lowRule, targetRule));

        Optional<CdssRiskMatrixRule> selected = selector.selectRule(
            "tenant-A",
            "runtime-H9",
            "result-review",
            RecommendationRiskLevel.HIGH,
            CdssAutomationLevel.INTERRUPTIVE
        );

        assertThat(selected).contains(targetRule);
    }

    @Test
    void ignoresDisabledRiskMatrixAndRejectsInvalidSourceRef() {
        ClinicalRuntimeReleaseItem disabledMatrix = matrixItem(
            PlatformTenant.ID, "av-disabled", ReleaseEntryState.DISABLED);
        when(runtime.resolve("tenant-A", "runtime-H9")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(disabledMatrix)));

        assertThat(selector.selectRule(
            "tenant-A", "runtime-H9", "result-review",
            RecommendationRiskLevel.HIGH, CdssAutomationLevel.INTERRUPTIVE))
            .isEmpty();

        ClinicalRuntimeReleaseItem invalidMatrix = matrixItem(
            PlatformTenant.ID, "av-invalid", ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "runtime-invalid")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(invalidMatrix)));
        when(assetVersions.findByVersionIdAndTenantId("av-invalid", PlatformTenant.ID))
            .thenReturn(Optional.of(assetVersion(PlatformTenant.ID, "av-invalid", "legacy-matrix:4")));

        assertThatThrownBy(() -> selector.selectRule(
            "tenant-A", "runtime-invalid", "result-review",
            RecommendationRiskLevel.HIGH, CdssAutomationLevel.INTERRUPTIVE))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("CDSS 风险矩阵资产来源无效");
    }

    private ClinicalRuntimeRelease release() {
        return new ClinicalRuntimeRelease(
            9L, "runtime-H9", "tenant-A", "hospital-A", 9L,
            "baseline-A13", "a".repeat(64), null,
            NOW, "tester", NOW, "tester", "trace-cdss-runtime");
    }

    private ClinicalRuntimeReleaseItem matrixItem(
            String sourceTenantId,
            String versionId,
            ReleaseEntryState state) {
        return new ClinicalRuntimeReleaseItem(
            1L,
            "runtime-H9",
            sourceTenantId,
            ReleaseSourceLayer.PLATFORM,
            VersionedAssetType.CDSS_RISK,
            "CDSS.RISK.MATRIX",
            state,
            versionId,
            "V4",
            "c".repeat(64),
            NOW,
            "tester",
            "trace-cdss-runtime"
        );
    }

    private AssetVersion assetVersion(String tenantId, String versionId, String sourceRef) {
        return new AssetVersion(
            null,
            versionId,
            tenantId,
            VersionedAssetType.CDSS_RISK,
            "CDSS.RISK.MATRIX",
            "V4",
            "ALL",
            "ALL",
            "c".repeat(64),
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
            "trace-cdss-runtime"
        );
    }

    private CdssRiskMatrixRule rule(
            String tenantId,
            String matrixId,
            RecommendationRiskLevel severity,
            CdssAutomationLevel automationLevel,
            RecommendationRiskLevel riskLevel) {
        return new CdssRiskMatrixRule(
            null,
            matrixId,
            tenantId,
            "result-review",
            severity,
            automationLevel,
            riskLevel,
            riskLevel == RecommendationRiskLevel.HIGH
                ? CdssReviewRequirement.PHYSICIAN_CONFIRMATION
                : CdssReviewRequirement.OPTIONAL_REVIEW,
            riskLevel == RecommendationRiskLevel.HIGH ? 72 : 0,
            riskLevel == RecommendationRiskLevel.HIGH
                ? "OPT04_SILENT_TRIAL"
                : "STANDARD_CHANGE_REVIEW",
            false,
            "NMPA_RESERVED",
            "TRACEABLE_EVIDENCE_REQUIRED",
            CdssRiskMatrixStatus.ACTIVE,
            "4",
            "矩阵规则命中",
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-cdss-runtime");
    }
}
