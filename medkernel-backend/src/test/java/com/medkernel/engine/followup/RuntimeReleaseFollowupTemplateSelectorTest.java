package com.medkernel.engine.followup;

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
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.PlatformTenant;

class RuntimeReleaseFollowupTemplateSelectorTest {

    private static final Instant NOW = Instant.parse("2026-06-23T11:00:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final AssetVersionRepository assetVersions = mock(AssetVersionRepository.class);
    private final FollowupTemplateRepository templates = mock(FollowupTemplateRepository.class);
    private final RuntimeReleaseFollowupTemplateSelector selector =
        new RuntimeReleaseFollowupTemplateSelector(runtime, assetVersions, templates);

    @Test
    void requiresTemplatePinnedByTheHospitalRuntimeRelease() {
        ClinicalRuntimeReleaseItem active = followupItem(
            "tenant-A", "FUP.COPD", "av-followup-v3", ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem disabled = followupItem(
            "tenant-A", "FUP.ASTHMA", "av-followup-v1", ReleaseEntryState.DISABLED);
        when(runtime.resolve("tenant-A", "runtime-H9")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(active, disabled)));
        when(assetVersions.findByVersionIdAndTenantId("av-followup-v3", "tenant-A"))
            .thenReturn(Optional.of(assetVersion("tenant-A", "FUP.COPD", "av-followup-v3")));
        FollowupTemplate template = template("tenant-A", "ftpl-copd-v3", "FUP.COPD", 3, "av-followup-v3");
        when(templates.findByTenantIdAndTemplateCodeAndVersionNo("tenant-A", "FUP.COPD", 3))
            .thenReturn(Optional.of(template));

        FollowupTemplate selected = selector.requireByTemplateId(
            "tenant-A", "runtime-H9", "ftpl-copd-v3");

        assertThat(selected.templateId()).isEqualTo("ftpl-copd-v3");
        assertThat(selected.templateCode()).isEqualTo("FUP.COPD");
        assertThat(selected.assetVersionId()).isEqualTo("av-followup-v3");
    }

    @Test
    void rejectsTemplateNotPresentInRuntimeRelease() {
        when(runtime.resolve("tenant-A", "runtime-H9")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of()));

        assertThatThrownBy(() -> selector.requireByTemplateId(
            "tenant-A", "runtime-H9", "ftpl-copd-v3"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("当前医院运行修订未启用随访模板");
    }

    private ClinicalRuntimeRelease release() {
        return new ClinicalRuntimeRelease(
            9L, "runtime-H9", "tenant-A", "hospital-A", 9L,
            "baseline-A13", "a".repeat(64), null,
            NOW, "tester", NOW, "tester", "trace-followup-runtime");
    }

    private ClinicalRuntimeReleaseItem followupItem(
            String sourceTenantId,
            String assetIdentity,
            String versionId,
            ReleaseEntryState state) {
        return new ClinicalRuntimeReleaseItem(
            1L,
            "runtime-H9",
            sourceTenantId,
            PlatformTenant.ID.equals(sourceTenantId) ? ReleaseSourceLayer.PLATFORM : ReleaseSourceLayer.HOSPITAL,
            VersionedAssetType.FOLLOWUP,
            assetIdentity,
            state,
            versionId,
            "V3",
            "d".repeat(64),
            NOW,
            "tester",
            "trace-followup-runtime"
        );
    }

    private AssetVersion assetVersion(String tenantId, String assetIdentity, String versionId) {
        return new AssetVersion(
            null,
            versionId,
            tenantId,
            VersionedAssetType.FOLLOWUP,
            assetIdentity,
            "V3",
            "tenant:tenant-A",
            "riskLevel=HIGH",
            "d".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            "version:" + versionId,
            "hospital://followup/copd",
            NOW,
            null,
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-followup-runtime"
        );
    }

    private FollowupTemplate template(
            String tenantId,
            String templateId,
            String templateCode,
            int versionNo,
            String assetVersionId) {
        return new FollowupTemplate(
            null,
            templateId,
            tenantId,
            templateCode,
            versionNo,
            "慢阻肺随访模板",
            null,
            "tenant:tenant-A",
            "riskLevel=HIGH",
            "[]",
            "{}",
            "{}",
            "hospital://followup/copd",
            assetVersionId,
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-followup-runtime"
        );
    }
}
