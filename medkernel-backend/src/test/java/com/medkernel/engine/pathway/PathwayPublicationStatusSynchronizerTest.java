package com.medkernel.engine.pathway;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

class PathwayPublicationStatusSynchronizerTest {

    private static final Instant NOW = Instant.parse("2026-06-24T13:00:00Z");

    private final PathwayTemplateRepository templates = mock(PathwayTemplateRepository.class);
    private final PathwayPublicationStatusSynchronizer synchronizer =
        new PathwayPublicationStatusSynchronizer(templates);

    @Test
    void marksMatchingDraftTemplatePublishedWhenUnifiedPathwayVersionIsPublished() {
        PathwayTemplate draft = template(PathwayTemplateStatus.DRAFT);
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion(
            "tenant-A", "PATH.CLINICAL.CYCLE", 1))
            .thenReturn(Optional.of(draft));

        synchronizer.afterPublished(
            version(VersionedAssetType.PATHWAY, "PATH.CLINICAL.CYCLE", "V1"),
            NOW,
            "operator-A",
            "trace-A");

        verify(templates).save(org.mockito.ArgumentMatchers.argThat(value ->
            value.templateId().equals("pt-cycle-v1")
                && value.status() == PathwayTemplateStatus.PUBLISHED
                && value.updatedAt().equals(NOW)
                && value.updatedBy().equals("operator-A")
                && value.traceId().equals("trace-A")));
    }

    @Test
    void ignoresNonPathwayPublishedVersions() {
        synchronizer.afterPublished(
            version(VersionedAssetType.RULE, "RULE.CLINICAL.CYCLE", "V1"),
            NOW,
            "operator-A",
            "trace-A");

        verifyNoInteractions(templates);
    }

    private AssetVersion version(
            VersionedAssetType assetType,
            String assetIdentity,
            String versionNo) {
        return new AssetVersion(
            1L,
            "av-" + assetIdentity,
            "tenant-A",
            assetType,
            assetIdentity,
            versionNo,
            "/tenant-A/group-A/hospital-A",
            "pathway:GENERAL",
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            "version:av-" + assetIdentity,
            "院内已审核路径制度",
            NOW,
            null,
            NOW.minusSeconds(3600),
            "operator-old",
            NOW,
            "operator-A",
            "trace-A"
        );
    }

    private PathwayTemplate template(PathwayTemplateStatus status) {
        return new PathwayTemplate(
            1L,
            "pt-cycle-v1",
            "tenant-A",
            "PATH.CLINICAL.CYCLE",
            "基础节点闭环",
            "GENERAL",
            1,
            PathwayTemplateLevel.STANDARD,
            status,
            PathwayEntryMode.AUTO_SUGGEST,
            "ASSESS",
            "院内已审核路径制度",
            "完成入径评估后进入处置确认或闭环安排。",
            "{}",
            "{}",
            NOW.minusSeconds(3600),
            "operator-old",
            NOW.minusSeconds(3600),
            "operator-old",
            "trace-old"
        );
    }
}
