package com.medkernel.engine.pathway;

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
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.AssetTriggerBinding;
import com.medkernel.engine.versioning.AssetTriggerBindingRepository;
import com.medkernel.engine.versioning.AssetTriggerPurpose;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.PlatformTenant;

class RuntimeReleasePathwaySelectorTest {

    private static final Instant NOW = Instant.parse("2026-06-23T05:30:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final PathwayTemplateRepository templates = mock(PathwayTemplateRepository.class);
    private final AssetTriggerBindingRepository triggers =
        mock(AssetTriggerBindingRepository.class);
    private final RuntimeReleasePathwaySelector selector =
        new RuntimeReleasePathwaySelector(runtime, templates, triggers);

    @Test
    void selectsEntryCandidatesFromExactRuntimeReleaseAndTriggerBinding() {
        ClinicalRuntimeReleaseItem platformPathway =
            pathwayItem(PlatformTenant.ID, "TPL.CKD", "V2", ReleaseSourceLayer.PLATFORM);
        ClinicalRuntimeReleaseItem hospitalPathway =
            pathwayItem("tenant-A", "TPL.COPD", "V3", ReleaseSourceLayer.HOSPITAL);
        when(runtime.resolve("tenant-A", "release-H7")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(platformPathway, hospitalPathway)));
        stubBinding(platformPathway, AssetTriggerPurpose.PATHWAY_ENTRY_CANDIDATE, "result-review");
        stubBinding(hospitalPathway, AssetTriggerPurpose.PATHWAY_ENTRY_CANDIDATE, "result-review");
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion(
                PlatformTenant.ID, "TPL.CKD", 2))
            .thenReturn(Optional.of(template(
                PlatformTenant.ID, "pt-ckd-v2", "TPL.CKD", 2)));
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion(
                "tenant-A", "TPL.COPD", 3))
            .thenReturn(Optional.of(template(
                "tenant-A", "pt-copd-v3", "TPL.COPD", 3)));

        RuntimePathwaySelection selection =
            selector.selectEntryCandidates("tenant-A", "release-H7", "result-review");

        assertThat(selection.runtimeReleaseId()).isEqualTo("release-H7");
        assertThat(selection.platformBaselineReleaseId()).isEqualTo("baseline-A12");
        assertThat(selection.pathways())
            .extracting(
                RuntimePathwayReference::sourceTenantId,
                RuntimePathwayReference::templateId,
                RuntimePathwayReference::pathwayVersionId)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                    PlatformTenant.ID, "pt-ckd-v2", "asset-version-TPL.CKD"),
                org.assertj.core.groups.Tuple.tuple(
                    "tenant-A", "pt-copd-v3", "asset-version-TPL.COPD"));
    }

    @Test
    void ignoresPathwayWithoutEntryBindingButRejectsMissingPinnedTemplate() {
        ClinicalRuntimeReleaseItem pathway =
            pathwayItem("tenant-A", "TPL.COPD", "V3", ReleaseSourceLayer.HOSPITAL);
        when(runtime.resolve("tenant-A", "release-H7")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(pathway)));

        assertThat(selector.selectEntryCandidates(
            "tenant-A", "release-H7", "result-review").pathways()).isEmpty();

        stubBinding(pathway, AssetTriggerPurpose.PATHWAY_ENTRY_CANDIDATE, "result-review");
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion(
                "tenant-A", "TPL.COPD", 3))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> selector.selectEntryCandidates(
            "tenant-A", "release-H7", "result-review"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("锁定路径版本不存在");
    }

    @Test
    void resolvesProgressAgainstPinnedPathwayVersionInsteadOfCurrentTemplatePointer() {
        ClinicalRuntimeReleaseItem pathway =
            pathwayItem("tenant-A", "TPL.COPD", "V1", ReleaseSourceLayer.HOSPITAL);
        when(runtime.resolve("tenant-A", "release-H3")).thenReturn(new ClinicalRuntimeReleaseContent(
            new ClinicalRuntimeRelease(
                3L, "release-H3", "tenant-A", "hospital-A", 3L,
                "baseline-A9", "a".repeat(64), null,
                NOW, "tester", NOW, "tester", "trace-pathway"),
            List.of(pathway)));
        stubBinding(pathway, AssetTriggerPurpose.PATHWAY_PROGRESS, "order-sign");
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion(
                "tenant-A", "TPL.COPD", 1))
            .thenReturn(Optional.of(template(
                "tenant-A", "pt-copd-v1", "TPL.COPD", 1)));

        RuntimePathwayReference selected = selector.requireProgressPathway(
            "tenant-A", "release-H3", "asset-version-TPL.COPD", "order-sign");

        assertThat(selected.templateId()).isEqualTo("pt-copd-v1");
        assertThat(selected.versionNo()).isEqualTo(1);
        assertThat(selected.pathwayVersionId()).isEqualTo("asset-version-TPL.COPD");
    }

    private void stubBinding(
            ClinicalRuntimeReleaseItem item,
            AssetTriggerPurpose purpose,
            String triggerPoint) {
        when(triggers
            .findByTenantIdAndVersionIdAndPurposeAndTriggerPointOrderByTriggerBindingIdAsc(
                item.sourceTenantId(), item.versionId(), purpose, triggerPoint))
            .thenReturn(List.of(new AssetTriggerBinding(
                1L,
                "trigger-" + item.versionId() + "-" + purpose,
                item.sourceTenantId(),
                VersionedAssetType.PATHWAY,
                item.assetIdentity(),
                item.versionId(),
                triggerPoint,
                purpose,
                "[]",
                NOW,
                "tester",
                NOW,
                "tester",
                "trace-pathway"
            )));
    }

    private ClinicalRuntimeRelease release() {
        return new ClinicalRuntimeRelease(
            7L, "release-H7", "tenant-A", "hospital-A", 7L,
            "baseline-A12", "a".repeat(64), null,
            NOW, "tester", NOW, "tester", "trace-pathway");
    }

    private ClinicalRuntimeReleaseItem pathwayItem(
            String sourceTenantId,
            String templateCode,
            String versionNo,
            ReleaseSourceLayer sourceLayer) {
        return new ClinicalRuntimeReleaseItem(
            1L,
            "release-H7",
            sourceTenantId,
            sourceLayer,
            VersionedAssetType.PATHWAY,
            templateCode,
            ReleaseEntryState.ACTIVE,
            "asset-version-" + templateCode,
            versionNo,
            "b".repeat(64),
            NOW,
            "tester",
            "trace-pathway"
        );
    }

    private PathwayTemplate template(
            String tenantId,
            String templateId,
            String templateCode,
            int versionNo) {
        return new PathwayTemplate(
            1L,
            templateId,
            tenantId,
            templateCode,
            templateCode,
            "DISEASE",
            versionNo,
            PathwayTemplateLevel.STANDARD,
            PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.MANUAL_CONFIRM,
            "START",
            "权威来源",
            null,
            "{}",
            "{}",
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-pathway"
        );
    }
}
