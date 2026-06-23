package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.shared.context.PlatformTenant;

class ClinicalRuntimeDeclarativeAssetResolverTest {

    private static final Instant NOW = Instant.parse("2026-06-23T04:00:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final AssetVersionRepository versions = mock(AssetVersionRepository.class);
    private final AssetVersionContentRepository contents = mock(AssetVersionContentRepository.class);
    private final DeclarativeAssetRuntimePort resolver =
        new ClinicalRuntimeDeclarativeAssetResolver(runtime, versions, contents);

    @Test
    void resolvesExactPublishedBodyFromTheHospitalRuntimeRelease() throws Exception {
        ClinicalRuntimeReleaseItem item = item(
            PlatformTenant.ID, "VS.ANTICOAGULANT", "av-1", "V2", "pending");
        String body = """
            {"schemaVersion":"1.0","name":"抗凝药物","codeSystem":"ATC","members":[{"code":"B01AA03","display":"华法林"}]}
            """.trim();
        String hash = sha256(body);
        item = item(PlatformTenant.ID, "VS.ANTICOAGULANT", "av-1", "V2", hash);
        when(runtime.resolve("tenant-A", "release-4")).thenReturn(content(item));
        when(versions.findByVersionIdAndTenantId("av-1", PlatformTenant.ID))
            .thenReturn(Optional.of(version(
                PlatformTenant.ID, "av-1", "VS.ANTICOAGULANT", "V2", hash,
                AssetVersionStatus.PUBLISHED)));
        when(contents.findByTenantIdAndVersionId(PlatformTenant.ID, "av-1"))
            .thenReturn(Optional.of(new AssetVersionContent(
                1L, "av-1", PlatformTenant.ID, body, hash,
                NOW, "operator", NOW, "operator", "trace")));

        ResolvedDeclarativeAsset resolved = resolver.resolve(
            "tenant-A", "release-4", VersionedAssetType.VALUE_SET, "VS.ANTICOAGULANT"
        ).orElseThrow();

        assertThat(resolved.assetVersion()).isEqualTo("V2");
        assertThat(resolved.runtimeReleaseId()).isEqualTo("release-4");
        assertThat(resolved.contentJson()).isEqualTo(body);
    }

    @Test
    void returnsEmptyForAssetOutsideReleaseAndRejectsNonPublishedBody() {
        ClinicalRuntimeReleaseItem item =
            item("tenant-A", "VS.DRAFT", "av-draft", "V1", "a".repeat(64));
        when(runtime.resolve("tenant-A", "release-4")).thenReturn(content(item));

        assertThat(resolver.resolve(
            "tenant-A", "release-4", VersionedAssetType.VALUE_SET, "VS.MISSING"))
            .isEmpty();

        when(versions.findByVersionIdAndTenantId("av-draft", "tenant-A"))
            .thenReturn(Optional.of(version(
                "tenant-A", "av-draft", "VS.DRAFT", "V1", "a".repeat(64),
                AssetVersionStatus.DRAFT)));
        assertThatThrownBy(() -> resolver.resolve(
            "tenant-A", "release-4", VersionedAssetType.VALUE_SET, "VS.DRAFT"))
            .hasMessageContaining("清单不一致");
    }

    private ClinicalRuntimeReleaseContent content(ClinicalRuntimeReleaseItem item) {
        ClinicalRuntimeRelease release = new ClinicalRuntimeRelease(
            4L, "release-4", "tenant-A", "hospital-A", 4L,
            "baseline-A8", "a".repeat(64), null,
            NOW, "operator", NOW, "operator", "trace");
        return new ClinicalRuntimeReleaseContent(release, List.of(item));
    }

    private ClinicalRuntimeReleaseItem item(
            String tenantId,
            String identity,
            String versionId,
            String versionNo,
            String hash) {
        return new ClinicalRuntimeReleaseItem(
            1L, "release-4", tenantId,
            PlatformTenant.isPlatformTenant(tenantId)
                ? ReleaseSourceLayer.PLATFORM : ReleaseSourceLayer.HOSPITAL,
            VersionedAssetType.VALUE_SET, identity, ReleaseEntryState.ACTIVE,
            versionId, versionNo, hash, NOW, "operator", "trace");
    }

    private AssetVersion version(
            String tenantId,
            String versionId,
            String identity,
            String versionNo,
            String hash,
            AssetVersionStatus status) {
        return new AssetVersion(
            1L, versionId, tenantId, VersionedAssetType.VALUE_SET, identity, versionNo,
            "tenant:" + tenantId, "ALL", hash,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            status, "version:" + versionId, "来源", null, null,
            NOW, "operator", NOW, "operator", "trace");
    }

    private String sha256(String content) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
