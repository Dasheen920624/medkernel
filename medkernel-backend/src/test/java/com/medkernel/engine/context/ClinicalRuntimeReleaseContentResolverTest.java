package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseManifestHash;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.PlatformTenant;

class ClinicalRuntimeReleaseContentResolverTest {

    private static final Instant NOW = Instant.parse("2026-06-23T03:00:00Z");
    private final ClinicalRuntimeReleaseRepository releases =
        mock(ClinicalRuntimeReleaseRepository.class);
    private final ClinicalRuntimeReleaseItemRepository items =
        mock(ClinicalRuntimeReleaseItemRepository.class);
    private final ClinicalRuntimeReleaseContentResolver resolver =
        new ClinicalRuntimeReleaseContentResolver(releases, items);

    @Test
    void resolvesAndVerifiesTheExactMaterializedManifest() {
        List<ClinicalRuntimeReleaseItem> manifest = List.of(
            item(VersionedAssetType.KNOWLEDGE, "KNOW.CKD", ReleaseEntryState.ACTIVE,
                "know-v2", "V2", "1".repeat(64)),
            item(VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.DISABLED,
                null, null, null)
        );
        ClinicalRuntimeRelease release = release(hash(manifest));
        when(releases.findByTenantIdAndReleaseId("tenant-A", "release-4"))
            .thenReturn(Optional.of(release));
        when(items.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc("release-4"))
            .thenReturn(manifest);

        ClinicalRuntimeReleaseContent result = resolver.resolve("tenant-A", "release-4");

        assertThat(result.release()).isEqualTo(release);
        assertThat(result.items())
            .extracting(
                ClinicalRuntimeReleaseItem::assetIdentity,
                ClinicalRuntimeReleaseItem::entryState,
                ClinicalRuntimeReleaseItem::versionNo)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                    "KNOW.CKD", ReleaseEntryState.ACTIVE, "V2"),
                org.assertj.core.groups.Tuple.tuple(
                    "RULE.CKD", ReleaseEntryState.DISABLED, null)
            );
    }

    @Test
    void rejectsMissingOrTamperedRuntimeManifest() {
        when(releases.findByTenantIdAndReleaseId("tenant-A", "missing"))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve("tenant-A", "missing"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("机构生效版本不存在");

        when(releases.findByTenantIdAndReleaseId("tenant-A", "release-4"))
            .thenReturn(Optional.of(release("0".repeat(64))));
        when(items.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc("release-4"))
            .thenReturn(List.of(item(
                VersionedAssetType.RULE, "RULE.CKD", ReleaseEntryState.ACTIVE,
                "rule-v1", "V1", "2".repeat(64))));

        assertThatThrownBy(() -> resolver.resolve("tenant-A", "release-4"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("明细校验码不一致");
    }

    private ClinicalRuntimeRelease release(String manifestHash) {
        return new ClinicalRuntimeRelease(
            4L, "release-4", "tenant-A", "hospital-A", 4L,
            "baseline-A8", manifestHash, null,
            NOW, "operator-A", NOW, "operator-A", "trace-A");
    }

    private ClinicalRuntimeReleaseItem item(
            VersionedAssetType type,
            String identity,
            ReleaseEntryState state,
            String versionId,
            String versionNo,
            String contentHash) {
        return new ClinicalRuntimeReleaseItem(
            null, "release-4", PlatformTenant.ID, ReleaseSourceLayer.PLATFORM,
            type, identity, state, versionId, versionNo, contentHash,
            NOW, "operator-A", "trace-A");
    }

    private String hash(List<ClinicalRuntimeReleaseItem> manifest) {
        return ReleaseManifestHash.sha256(manifest.stream().map(item -> String.join(
            "\u001f",
            item.sourceTenantId(),
            item.sourceLayer().name(),
            item.assetType().name(),
            item.assetIdentity(),
            item.entryState().name(),
            item.versionId() == null ? "" : item.versionId(),
            item.versionNo() == null ? "" : item.versionNo(),
            item.contentHash() == null ? "" : item.contentHash()
        )).toList());
    }
}
