package com.medkernel.engine.knowledge.diagnosis.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
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
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.PlatformTenant;

class RuntimeReleaseDiagnosisSelectorTest {

    private static final Instant NOW = Instant.parse("2026-06-23T08:00:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final KnowledgeIdentityRepository identities =
        mock(KnowledgeIdentityRepository.class);
    private final KnowledgeAssetVersionRepository versions =
        mock(KnowledgeAssetVersionRepository.class);
    private final RuntimeReleaseDiagnosisSelector selector =
        new RuntimeReleaseDiagnosisSelector(runtime, identities, versions);

    @Test
    void selectsOnlyDiagnosisKnowledgeFromCurrentRuntimeRelease() {
        ClinicalRuntimeReleaseItem platformDiagnosis =
            item(PlatformTenant.ID, "DX.PNEU", "v1.0", ReleaseSourceLayer.PLATFORM, ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem hospitalDiagnosis =
            item("tenant-A", "DX.COPD", "v2.0", ReleaseSourceLayer.HOSPITAL, ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem guideline =
            item(PlatformTenant.ID, "GUIDE.CAP", "v3.0", ReleaseSourceLayer.PLATFORM, ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem disabledDiagnosis =
            item("tenant-A", "DX.OLD", "v1.0", ReleaseSourceLayer.HOSPITAL, ReleaseEntryState.DISABLED);
        when(runtime.resolve("tenant-A", "release-DX1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(platformDiagnosis, hospitalDiagnosis, guideline, disabledDiagnosis)));
        stubIdentity(PlatformTenant.ID, "DX.PNEU", 100L, KnowledgeDomain.DIAGNOSIS, "社区获得性肺炎");
        stubIdentity("tenant-A", "DX.COPD", 200L, KnowledgeDomain.DIAGNOSIS, "慢阻肺急性加重");
        stubIdentity(PlatformTenant.ID, "GUIDE.CAP", 300L, KnowledgeDomain.GUIDELINE, "肺炎诊疗指南");
        when(versions.findByTenantIdAndIdentityIdAndVersionNo(PlatformTenant.ID, 100L, "v1.0"))
            .thenReturn(Optional.of(version(10L, PlatformTenant.ID, 100L, "v1.0", SourceAuthorityLevel.A_REGULATION)));
        when(versions.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 200L, "v2.0"))
            .thenReturn(Optional.of(version(20L, "tenant-A", 200L, "v2.0", SourceAuthorityLevel.B_GUIDELINE)));

        List<RuntimeDiagnosisReference> selected = selector.select("tenant-A", "release-DX1");

        assertThat(selected)
            .extracting(
                RuntimeDiagnosisReference::sourceTenantId,
                RuntimeDiagnosisReference::identityCode,
                RuntimeDiagnosisReference::diagnosisName,
                RuntimeDiagnosisReference::knowledgeVersionId,
                RuntimeDiagnosisReference::versionNo)
            .containsExactly(
                tuple(PlatformTenant.ID, "DX.PNEU", "社区获得性肺炎", 10L, "v1.0"),
                tuple("tenant-A", "DX.COPD", "慢阻肺急性加重", 20L, "v2.0"));
    }

    @Test
    void rejectsRuntimeReleaseWhenPinnedDiagnosisVersionIsMissing() {
        ClinicalRuntimeReleaseItem diagnosis =
            item("tenant-A", "DX.COPD", "v2.0", ReleaseSourceLayer.HOSPITAL, ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "release-DX1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(diagnosis)));
        stubIdentity("tenant-A", "DX.COPD", 200L, KnowledgeDomain.DIAGNOSIS, "慢阻肺急性加重");
        when(versions.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 200L, "v2.0"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> selector.select("tenant-A", "release-DX1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("锁定诊断知识版本不存在");
    }

    @Test
    void rejectsRuntimeReleaseWhenPinnedDiagnosisVersionIsNotActive() {
        ClinicalRuntimeReleaseItem diagnosis =
            item("tenant-A", "DX.COPD", "v2.0", ReleaseSourceLayer.HOSPITAL, ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "release-DX1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(diagnosis)));
        stubIdentity("tenant-A", "DX.COPD", 200L, KnowledgeDomain.DIAGNOSIS, "慢阻肺急性加重");
        when(versions.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 200L, "v2.0"))
            .thenReturn(Optional.of(version(
                20L, "tenant-A", 200L, "v2.0", KnowledgeVersionStatus.SUPERSEDED)));

        assertThatThrownBy(() -> selector.select("tenant-A", "release-DX1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("锁定诊断知识版本未激活");
    }

    private void stubIdentity(
            String tenantId,
            String identityCode,
            Long identityId,
            KnowledgeDomain domain,
            String subject) {
        when(identities.findByTenantIdAndIdentityCode(tenantId, identityCode))
            .thenReturn(Optional.of(new KnowledgeIdentity(
                identityId,
                tenantId,
                identityCode,
                domain,
                subject,
                null,
                null,
                KnowledgeIdentityStatus.ACTIVE,
                null,
                NOW,
                "tester",
                NOW,
                "tester"
            )));
    }

    private ClinicalRuntimeRelease release() {
        return new ClinicalRuntimeRelease(
            1L,
            "release-DX1",
            "tenant-A",
            "hospital-A",
            1L,
            "baseline-DX",
            "a".repeat(64),
            null,
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-dx"
        );
    }

    private ClinicalRuntimeReleaseItem item(
            String sourceTenantId,
            String identityCode,
            String versionNo,
            ReleaseSourceLayer sourceLayer,
            ReleaseEntryState entryState) {
        return new ClinicalRuntimeReleaseItem(
            1L,
            "release-DX1",
            sourceTenantId,
            sourceLayer,
            VersionedAssetType.KNOWLEDGE,
            identityCode,
            entryState,
            "asset-version-" + identityCode,
            versionNo,
            "b".repeat(64),
            NOW,
            "tester",
            "trace-dx"
        );
    }

    private KnowledgeAssetVersion version(
            Long id,
            String tenantId,
            Long identityId,
            String versionNo,
            SourceAuthorityLevel authority) {
        return version(id, tenantId, identityId, versionNo, KnowledgeVersionStatus.ACTIVE, authority);
    }

    private KnowledgeAssetVersion version(
            Long id,
            String tenantId,
            Long identityId,
            String versionNo,
            KnowledgeVersionStatus status) {
        return version(id, tenantId, identityId, versionNo, status, SourceAuthorityLevel.B_GUIDELINE);
    }

    private KnowledgeAssetVersion version(
            Long id,
            String tenantId,
            Long identityId,
            String versionNo,
            KnowledgeVersionStatus status,
            SourceAuthorityLevel authority) {
        return new KnowledgeAssetVersion(
            id,
            tenantId,
            identityId,
            versionNo,
            null,
            null,
            null,
            "h" + id,
            "[]",
            status,
            KnowledgeRiskLevel.LOW,
            authority,
            GradeEvidenceQuality.MODERATE,
            GradeRecommendationStrength.WEAK,
            null,
            "tenant:" + tenantId,
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            "scope-" + id,
            null,
            null,
            null,
            null,
            NOW,
            null,
            null,
            null,
            NOW,
            "tester",
            NOW,
            "tester",
            12,
            null
        );
    }
}
