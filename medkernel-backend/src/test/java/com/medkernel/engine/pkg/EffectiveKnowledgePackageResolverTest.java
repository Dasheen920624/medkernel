package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.BatchResolvedAsset;
import com.medkernel.engine.versioning.InheritanceBatchResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetIdentity;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

class EffectiveKnowledgePackageResolverTest {

    private KnowledgePackageRepository packageRepository;
    private PackageItemRepository itemRepository;
    private InheritanceResolver inheritanceResolver;
    private PackageEntitlementService entitlementService;
    private AssetVersionRepository assetVersionRepository;
    private EffectiveKnowledgePackageResolver resolver;

    @BeforeEach
    void setUp() {
        packageRepository = mock(KnowledgePackageRepository.class);
        itemRepository = mock(PackageItemRepository.class);
        inheritanceResolver = mock(InheritanceResolver.class);
        entitlementService = mock(PackageEntitlementService.class);
        assetVersionRepository = mock(AssetVersionRepository.class);
        resolver = new EffectiveKnowledgePackageResolver(
            packageRepository, itemRepository, inheritanceResolver, entitlementService,
            assetVersionRepository);
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
        when(inheritanceResolver.resolveBatch(any())).thenReturn(List.of(
            new BatchResolvedAsset(
                new VersionedAssetIdentity(VersionedAssetType.RULE, "RULE.VTE"),
                new ResolvedAssetVersion(
                    assetVersion("av-rule-2", VersionedAssetType.RULE, "RULE.VTE", "2"),
                    "/TENANT-A/HOSP-A",
                    false,
                    true,
                    false,
                    null,
                    SourceTier.ORG),
                false),
            new BatchResolvedAsset(
                new VersionedAssetIdentity(VersionedAssetType.PATHWAY, "PATH.COPD"),
                new ResolvedAssetVersion(
                    null,
                    "/TENANT-A/HOSP-A",
                    false,
                    true,
                    true,
                    null,
                    SourceTier.ORG),
                false)));

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
    void resolvesDeclaredItemsAndTenantAddsThroughOneBatchResolution() {
        KnowledgePackage pack = platformPackage("pkg-platform", KnowledgePackageStatus.ACTIVE);
        PackageItem declaredRule = platformItem(VersionedAssetType.RULE, "RULE.BASELINE", "1");
        VersionedAssetIdentity declaredIdentity =
            new VersionedAssetIdentity(VersionedAssetType.RULE, "RULE.BASELINE");
        VersionedAssetIdentity addedIdentity =
            new VersionedAssetIdentity(VersionedAssetType.PATHWAY, "PATH.LOCAL.ADD");
        AssetVersion declaredVersion =
            assetVersion("av-rule-baseline", VersionedAssetType.RULE, "RULE.BASELINE", "2");
        AssetVersion addedVersion =
            assetVersion("av-path-local", VersionedAssetType.PATHWAY, "PATH.LOCAL.ADD", "1");
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                PlatformTenant.ID, "PKG.BASELINE", "2026.06"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId(PlatformTenant.ID, "pkg-platform"))
            .thenReturn(List.of(declaredRule));
        when(inheritanceResolver.resolveBatch(any())).thenReturn(List.of(
            new BatchResolvedAsset(
                declaredIdentity,
                new ResolvedAssetVersion(
                    declaredVersion,
                    "/TENANT-A/HOSP-A",
                    false,
                    true,
                    false,
                    null,
                    SourceTier.ORG),
                false),
            new BatchResolvedAsset(
                addedIdentity,
                new ResolvedAssetVersion(
                    addedVersion,
                    "/TENANT-A/HOSP-A",
                    false,
                    true,
                    false,
                    null,
                    SourceTier.ORG),
                true)));

        EffectiveKnowledgePackageResponse response =
            resolver.resolve("tenant-A", "PKG.BASELINE", "2026.06", "dept-1");

        assertThat(response.items()).extracting(EffectivePackageItem::assetId)
            .containsExactly("RULE.BASELINE", "PATH.LOCAL.ADD");
        assertThat(response.items().get(0).declaredVersion()).isEqualTo("1");
        assertThat(response.items().get(1).declaredVersion()).isEqualTo("1");
        assertThat(response.items().get(1).sourceVersionId()).isEqualTo("av-path-local");
        verify(inheritanceResolver).resolveBatch(any());
        verify(inheritanceResolver, never()).resolve(any());
    }

    @Test
    void resolvesTenantTerminologySnapshotFromOwningPackageVersion() {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        KnowledgePackage pack = new KnowledgePackage(
            2L,
            "pkg-term-local",
            "tenant-A",
            "TERM.LAB",
            "2026.06",
            "检验术语映射",
            "术语快照",
            PackageAccessPolicy.OPEN,
            KnowledgePackageStatus.DRAFT,
            now,
            "tenant-admin",
            now,
            "tenant-admin",
            "trace-term"
        );
        PackageItem terminology = new PackageItem(
            3L,
            "item-term-local",
            "tenant-A",
            "pkg-term-local",
            VersionedAssetType.TERMINOLOGY,
            "TERM.LAB|HOSPITAL|hospital-A",
            "2026.06",
            now,
            "tenant-admin",
            now,
            "tenant-admin",
            "trace-term"
        );
        AssetVersion packageVersion = assetVersion(
            "av-term-package",
            VersionedAssetType.PACKAGE,
            "TERM.LAB",
            "2026.06"
        );
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                "tenant-A", "TERM.LAB", "2026.06"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-term-local"))
            .thenReturn(List.of(terminology));
        when(assetVersionRepository.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                "tenant-A", VersionedAssetType.PACKAGE, "TERM.LAB", "2026.06"))
            .thenReturn(Optional.of(packageVersion));

        EffectiveKnowledgePackageResponse response =
            resolver.resolve("tenant-A", "TERM.LAB", "2026.06", "hospital-A");

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.assetType()).isEqualTo(VersionedAssetType.TERMINOLOGY);
            assertThat(item.assetId()).isEqualTo("TERM.LAB|HOSPITAL|hospital-A");
            assertThat(item.sourceVersionId()).isEqualTo("av-term-package");
            assertThat(item.sourceTier()).isEqualTo(SourceTier.ORG);
        });
        verify(inheritanceResolver, never()).resolveBatch(any());
        verify(entitlementService, never()).assertUsable(any(), any());
    }

    @Test
    void resolvesCkdPathwayKnowledgePackageWithFormulaValueSetsFieldCatalogAndLocalOverride() {
        KnowledgePackage pack = platformPackage("pkg-platform", KnowledgePackageStatus.ACTIVE);
        List<PackageItem> ckdItems = List.of(
            platformItem(VersionedAssetType.PATHWAY, "PATH.CKD", "1"),
            platformItem(VersionedAssetType.RULE, "RULE.CKD.NEPHROTOXIC", "1"),
            platformItem(VersionedAssetType.VALUE_SET, "VS.ATC.NEPHROTOXIC", "2026.06"),
            platformItem(VersionedAssetType.VALUE_SET, "VS.LOINC.CREATININE", "2026.06"),
            platformItem(VersionedAssetType.FIELD_CATALOG, "FIELD.CKD.BINDING", "2026.06"),
            platformItem(VersionedAssetType.FORMULA, "CKD_EPI_2021_EGFR", "2026.06"),
            platformItem(VersionedAssetType.FORMULA, "COCKCROFT_GAULT_CRCL", "2026.06"),
            platformItem(VersionedAssetType.CONDITION_FRAGMENT, "FRAG.RENAL_LIMITED", "1"),
            platformItem(VersionedAssetType.EVALUATION, "EVAL.CKD.OUTCOME", "1")
        );
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                PlatformTenant.ID, "PKG.CKD", "2026.06"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId(PlatformTenant.ID, "pkg-platform"))
            .thenReturn(ckdItems);
        when(inheritanceResolver.resolveBatch(any())).thenAnswer(inv -> {
            InheritanceBatchResolveQuery query = inv.getArgument(0);
            return query.declaredAssets().stream().map(identity -> {
                String versionNo = identity.assetIdentity().equals("RULE.CKD.NEPHROTOXIC")
                ? "2"
                : "2026.06";
                boolean overridden = identity.assetIdentity().equals("RULE.CKD.NEPHROTOXIC");
                return new BatchResolvedAsset(
                    identity,
                    new ResolvedAssetVersion(
                        assetVersion("av-" + identity.assetType() + "-" + identity.assetIdentity(),
                            identity.assetType(), identity.assetIdentity(), versionNo),
                        overridden ? "/TENANT-A/HOSP-A/NEPH" : "/PLATFORM/CKD",
                        !overridden,
                        overridden,
                        false,
                        null,
                        overridden ? SourceTier.ORG : SourceTier.PLATFORM),
                    false);
            }).toList();
        });

        EffectivePackageSnapshot snapshot = EffectivePackageSnapshot.from(
            resolver.resolve("tenant-A", "PKG.CKD", "2026.06", "dept-neph"));

        assertThat(snapshot.items())
            .extracting(EffectivePackageItem::assetType)
            .containsExactly(
                VersionedAssetType.PATHWAY,
                VersionedAssetType.RULE,
                VersionedAssetType.VALUE_SET,
                VersionedAssetType.VALUE_SET,
                VersionedAssetType.FIELD_CATALOG,
                VersionedAssetType.FORMULA,
                VersionedAssetType.FORMULA,
                VersionedAssetType.CONDITION_FRAGMENT,
                VersionedAssetType.EVALUATION);
        assertThat(snapshot.items()).anySatisfy(item -> {
            assertThat(item.assetType()).isEqualTo(VersionedAssetType.RULE);
            assertThat(item.assetId()).isEqualTo("RULE.CKD.NEPHROTOXIC");
            assertThat(item.declaredVersion()).isEqualTo("1");
            assertThat(item.effectiveVersion()).isEqualTo("2");
            assertThat(item.overridden()).isTrue();
            assertThat(item.sourceOrgPath()).isEqualTo("/TENANT-A/HOSP-A/NEPH");
        });
        assertThat(snapshot.items()).anySatisfy(item -> {
            assertThat(item.assetType()).isEqualTo(VersionedAssetType.FORMULA);
            assertThat(item.assetId()).isEqualTo("CKD_EPI_2021_EGFR");
            assertThat(item.inherited()).isTrue();
            assertThat(item.sourceTier()).isEqualTo(SourceTier.PLATFORM);
        });
        assertThat(snapshot.excludedItems()).isEmpty();
        assertThat(snapshot.contentSha256()).matches("[a-f0-9]{64}");
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
        when(inheritanceResolver.resolveBatch(any())).thenReturn(List.of());

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

    @Test
    void resolvesOwnedPlatformDraftAsLifecycleCandidate() {
        KnowledgePackage pack = platformPackage("pkg-platform", KnowledgePackageStatus.DRAFT);
        when(itemRepository.findByTenantIdAndPackageId(PlatformTenant.ID, "pkg-platform"))
            .thenReturn(List.of());

        EffectiveKnowledgePackageResponse response = resolver.resolveOwnedLifecycleCandidate(
            PlatformTenant.ID, pack, "platform-root");

        assertThat(response.tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(response.targetOrgUnitId()).isEqualTo("platform-root");
        assertThat(response.packageId()).isEqualTo("pkg-platform");
        assertThat(response.items()).isEmpty();
        verify(packageRepository, never())
            .findByTenantIdAndPackageCodeAndPackageVersion(any(), any(), any());
    }

    @Test
    void rejectsRestrictedPackageBeforeLoadingDeclaredItems() {
        KnowledgePackage pack = platformPackage(
            "pkg-platform",
            KnowledgePackageStatus.ACTIVE,
            PackageAccessPolicy.ENTITLED);
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                PlatformTenant.ID, "PKG.BASELINE", "2026.06"))
            .thenReturn(Optional.of(pack));
        org.mockito.Mockito.doThrow(new ApiException(
                ErrorCode.NOT_FOUND,
                "平台知识包不可用"))
            .when(entitlementService)
            .assertUsable("tenant-A", pack);

        assertThatThrownBy(() -> resolver.resolve("tenant-A", "PKG.BASELINE", "2026.06", "dept-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);

        verify(itemRepository, never()).findByTenantIdAndPackageId(any(), any());
        verify(inheritanceResolver, never()).resolve(any());
        verify(inheritanceResolver, never()).resolveBatch(any());
    }

    private KnowledgePackage platformPackage(String packageId, KnowledgePackageStatus status) {
        return platformPackage(packageId, status, PackageAccessPolicy.OPEN);
    }

    private KnowledgePackage platformPackage(
            String packageId,
            KnowledgePackageStatus status,
            PackageAccessPolicy accessPolicy) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new KnowledgePackage(
            1L,
            packageId,
            PlatformTenant.ID,
            "PKG.BASELINE",
            "2026.06",
            "平台基线包",
            null,
            accessPolicy,
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
            AssetVersionStatus.PUBLISHED,
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
