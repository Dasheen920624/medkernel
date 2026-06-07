package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

class EffectiveKnowledgePackageResolverTest {

    private KnowledgePackageRepository packageRepository;
    private PackageItemRepository itemRepository;
    private InheritanceResolver inheritanceResolver;
    private EffectiveKnowledgePackageResolver resolver;

    @BeforeEach
    void setUp() {
        packageRepository = mock(KnowledgePackageRepository.class);
        itemRepository = mock(PackageItemRepository.class);
        inheritanceResolver = mock(InheritanceResolver.class);
        resolver = new EffectiveKnowledgePackageResolver(packageRepository, itemRepository, inheritanceResolver);
    }

    @Test
    void resolvesPlatformPackageItemsThroughInheritanceResolver() {
        KnowledgePackage pack = platformPackage("pkg-platform", KnowledgePackageStatus.ACTIVE);
        PackageItem rule = platformItem(VersionedAssetType.RULE, "RULE.VTE", "1");
        PackageItem pathway = platformItem(VersionedAssetType.PATHWAY, "PATH.COPD", "1");
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                PlatformTenant.ID, "PKG.BASELINE", "2026.06"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId(PlatformTenant.ID, "pkg-platform"))
            .thenReturn(List.of(rule, pathway));
        when(inheritanceResolver.resolve(any())).thenAnswer(inv -> {
            var query = (com.medkernel.engine.versioning.InheritanceResolveQuery) inv.getArgument(0);
            if (query.assetType() == VersionedAssetType.RULE) {
                return new ResolvedAssetVersion(
                    assetVersion("av-rule-2", VersionedAssetType.RULE, "RULE.VTE", "2"),
                    "/TENANT-A/HOSP-A",
                    false,
                    true,
                    false,
                    null,
                    SourceTier.ORG);
            }
            return new ResolvedAssetVersion(
                null,
                "/TENANT-A/HOSP-A",
                false,
                true,
                true,
                null,
                SourceTier.ORG);
        });

        EffectiveKnowledgePackageResponse response =
            resolver.resolve("tenant-A", "PKG.BASELINE", "2026.06", "dept-1");

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.assetType()).isEqualTo(VersionedAssetType.RULE);
            assertThat(item.assetId()).isEqualTo("RULE.VTE");
            assertThat(item.declaredVersion()).isEqualTo("1");
            assertThat(item.effectiveVersion()).isEqualTo("2");
            assertThat(item.sourceTier()).isEqualTo(SourceTier.ORG);
            assertThat(item.overridden()).isTrue();
        });
        assertThat(response.excludedItems()).singleElement().satisfies(exclusion -> {
            assertThat(exclusion.assetType()).isEqualTo(VersionedAssetType.PATHWAY);
            assertThat(exclusion.assetId()).isEqualTo("PATH.COPD");
            assertThat(exclusion.reason()).contains("停用");
        });
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void rejectsPackageItemWhenUnifiedVersionMappingIsMissing() {
        KnowledgePackage pack = platformPackage("pkg-platform", KnowledgePackageStatus.PUBLISHED);
        PackageItem evaluation = platformItem(VersionedAssetType.EVALUATION, "EVAL.VTE", "1");
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                PlatformTenant.ID, "PKG.BASELINE", "2026.06"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId(PlatformTenant.ID, "pkg-platform"))
            .thenReturn(List.of(evaluation));
        when(inheritanceResolver.resolve(any()))
            .thenThrow(new ApiException(ErrorCode.NOT_FOUND, "未找到可继承的 ACTIVE 资产版本"));

        assertThatThrownBy(() -> resolver.resolve("tenant-A", "PKG.BASELINE", "2026.06", "dept-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("有效包条目未接入统一版本资产")
            .hasMessageContaining("EVALUATION:EVAL.VTE@1");
    }

    @Test
    void rejectsUnreleasedPlatformBaselinePackage() {
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                PlatformTenant.ID, "PKG.BASELINE", "2026.06"))
            .thenReturn(Optional.of(platformPackage("pkg-platform", KnowledgePackageStatus.DRAFT)));

        assertThatThrownBy(() -> resolver.resolve("tenant-A", "PKG.BASELINE", "2026.06", "dept-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    private KnowledgePackage platformPackage(String packageId, KnowledgePackageStatus status) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new KnowledgePackage(
            1L,
            packageId,
            PlatformTenant.ID,
            "PKG.BASELINE",
            "2026.06",
            "平台基线包",
            null,
            status,
            now,
            "platform-admin",
            now,
            "platform-admin",
            "trace-pkg");
    }

    private PackageItem platformItem(VersionedAssetType type, String assetId, String assetVersion) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new PackageItem(
            null,
            "item-" + assetId,
            PlatformTenant.ID,
            "pkg-platform",
            type,
            assetId,
            assetVersion,
            now,
            "platform-admin",
            now,
            "platform-admin",
            "trace-pkg");
    }

    private AssetVersion assetVersion(
            String versionId,
            VersionedAssetType type,
            String assetIdentity,
            String versionNo) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new AssetVersion(
            1L,
            versionId,
            "tenant-A",
            type,
            assetIdentity,
            versionNo,
            "/TENANT-A/HOSP-A",
            "2026.06",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.ACTIVE,
            assetIdentity + "|/TENANT-A/HOSP-A|2026.06",
            "test/" + assetIdentity,
            null,
            null,
            now,
            "tenant-admin",
            now,
            "tenant-admin",
            "trace-version");
    }
}
