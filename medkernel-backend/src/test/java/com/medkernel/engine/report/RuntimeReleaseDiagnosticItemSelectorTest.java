package com.medkernel.engine.report;

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

class RuntimeReleaseDiagnosticItemSelectorTest {

    private static final Instant NOW = Instant.parse("2026-06-24T08:00:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final KnowledgeIdentityRepository identities =
        mock(KnowledgeIdentityRepository.class);
    private final KnowledgeAssetVersionRepository versions =
        mock(KnowledgeAssetVersionRepository.class);
    private final RuntimeReleaseDiagnosticItemSelector selector =
        new RuntimeReleaseDiagnosticItemSelector(runtime, identities, versions);

    @Test
    void selectsOnlyDiagnosticItemKnowledgeFromCurrentRuntimeRelease() {
        ClinicalRuntimeReleaseItem platformItem =
            item(PlatformTenant.ID, "LAB.POTASSIUM", "v1.0", ReleaseSourceLayer.PLATFORM, ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem hospitalItem =
            item("tenant-A", "IMG.CT.CHEST", "v2.0", ReleaseSourceLayer.HOSPITAL, ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem guideline =
            item(PlatformTenant.ID, "GUIDE.CAP", "v3.0", ReleaseSourceLayer.PLATFORM, ReleaseEntryState.ACTIVE);
        ClinicalRuntimeReleaseItem disabledItem =
            item("tenant-A", "LAB.OLD", "v1.0", ReleaseSourceLayer.HOSPITAL, ReleaseEntryState.DISABLED);
        when(runtime.resolve("tenant-A", "release-report-1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(platformItem, hospitalItem, guideline, disabledItem)));
        stubIdentity(PlatformTenant.ID, "LAB.POTASSIUM", 100L, KnowledgeDomain.DIAGNOSTIC_ITEM, "血钾检验说明书");
        stubIdentity("tenant-A", "IMG.CT.CHEST", 200L, KnowledgeDomain.DIAGNOSTIC_ITEM, "胸部 CT 说明书");
        stubIdentity(PlatformTenant.ID, "GUIDE.CAP", 300L, KnowledgeDomain.GUIDELINE, "肺炎诊疗指南");
        when(versions.findByTenantIdAndIdentityIdAndContentHash(
                PlatformTenant.ID, 100L, platformItem.contentHash()))
            .thenReturn(Optional.of(version(10L, PlatformTenant.ID, 100L, "v1.0", SourceAuthorityLevel.B_GUIDELINE)));
        when(versions.findByTenantIdAndIdentityIdAndContentHash(
                "tenant-A", 200L, hospitalItem.contentHash()))
            .thenReturn(Optional.of(version(20L, "tenant-A", 200L, "v2.0", SourceAuthorityLevel.C_CONSENSUS_LITERATURE)));

        List<RuntimeDiagnosticItemReference> selected = selector.select("tenant-A", "release-report-1");

        assertThat(selected)
            .extracting(
                RuntimeDiagnosticItemReference::sourceTenantId,
                RuntimeDiagnosticItemReference::itemCode,
                RuntimeDiagnosticItemReference::itemName,
                RuntimeDiagnosticItemReference::knowledgeVersionId,
                RuntimeDiagnosticItemReference::versionNo)
            .containsExactly(
                tuple(PlatformTenant.ID, "LAB.POTASSIUM", "血钾检验说明书", 10L, "v1.0"),
                tuple("tenant-A", "IMG.CT.CHEST", "胸部 CT 说明书", 20L, "v2.0"));
    }

    @Test
    void resolvesUnifiedAssetVersionToKnowledgeVersionByContentHash() {
        String contentHash = "c".repeat(64);
        ClinicalRuntimeReleaseItem platformItem =
            item(PlatformTenant.ID, "LAB.POTASSIUM", "V1", contentHash,
                ReleaseSourceLayer.PLATFORM, ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "release-report-1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(platformItem)));
        stubIdentity(PlatformTenant.ID, "LAB.POTASSIUM", 100L, KnowledgeDomain.DIAGNOSTIC_ITEM, "血钾检验说明书");
        when(versions.findByTenantIdAndIdentityIdAndContentHash(PlatformTenant.ID, 100L, contentHash))
            .thenReturn(Optional.of(version(
                10L,
                PlatformTenant.ID,
                100L,
                "ai-draft-task-diagnostic-item",
                KnowledgeVersionStatus.ACTIVE,
                SourceAuthorityLevel.B_GUIDELINE,
                contentHash)));

        List<RuntimeDiagnosticItemReference> selected = selector.select("tenant-A", "release-report-1");

        assertThat(selected)
            .extracting(
                RuntimeDiagnosticItemReference::knowledgeVersionId,
                RuntimeDiagnosticItemReference::versionNo)
            .containsExactly(tuple(10L, "ai-draft-task-diagnostic-item"));
    }

    @Test
    void rejectsRuntimeReleaseWhenPinnedDiagnosticItemVersionIsNotActive() {
        ClinicalRuntimeReleaseItem item =
            item("tenant-A", "IMG.CT.CHEST", "v2.0", ReleaseSourceLayer.HOSPITAL, ReleaseEntryState.ACTIVE);
        when(runtime.resolve("tenant-A", "release-report-1")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(item)));
        stubIdentity("tenant-A", "IMG.CT.CHEST", 200L, KnowledgeDomain.DIAGNOSTIC_ITEM, "胸部 CT 说明书");
        when(versions.findByTenantIdAndIdentityIdAndContentHash(
                "tenant-A", 200L, item.contentHash()))
            .thenReturn(Optional.of(version(
                20L, "tenant-A", 200L, "v2.0", KnowledgeVersionStatus.SUPERSEDED)));

        assertThatThrownBy(() -> selector.select("tenant-A", "release-report-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("锁定医技项目说明书版本未激活");
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
            "release-report-1",
            "tenant-A",
            "hospital-A",
            1L,
            "baseline-report",
            "a".repeat(64),
            null,
            NOW,
            "tester",
            NOW,
            "tester",
            "trace-report"
        );
    }

    private ClinicalRuntimeReleaseItem item(
            String sourceTenantId,
            String identityCode,
            String versionNo,
            ReleaseSourceLayer sourceLayer,
            ReleaseEntryState entryState) {
        return item(sourceTenantId, identityCode, versionNo, "b".repeat(64), sourceLayer, entryState);
    }

    private ClinicalRuntimeReleaseItem item(
            String sourceTenantId,
            String identityCode,
            String versionNo,
            String contentHash,
            ReleaseSourceLayer sourceLayer,
            ReleaseEntryState entryState) {
        return new ClinicalRuntimeReleaseItem(
            1L,
            "release-report-1",
            sourceTenantId,
            sourceLayer,
            VersionedAssetType.KNOWLEDGE,
            identityCode,
            entryState,
            "asset-version-" + identityCode,
            versionNo,
            contentHash,
            NOW,
            "tester",
            "trace-report"
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
        return version(id, tenantId, identityId, versionNo, status, authority, "h" + id);
    }

    private KnowledgeAssetVersion version(
            Long id,
            String tenantId,
            Long identityId,
            String versionNo,
            KnowledgeVersionStatus status,
            SourceAuthorityLevel authority,
            String contentHash) {
        return new KnowledgeAssetVersion(
            id,
            tenantId,
            identityId,
            versionNo,
            null,
            null,
            null,
            contentHash,
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
