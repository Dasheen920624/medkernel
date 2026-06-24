package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.context.OrgLevel;

class InheritanceResolverBatchTest {

    private static final Instant EFFECTIVE_AT = Instant.parse("2026-06-09T08:00:00Z");
    private static final String TENANT_ID = "tenant-A";
    private static final String GROUP_PATH = "/TENANT-A/GROUP-A";
    private static final String HOSPITAL_PATH = "/TENANT-A/GROUP-A/HOSP-A";

    private AssetVersionRepository assetVersions;
    private InheritanceOverrideRepository overrides;
    private OrgHierarchyRepository hierarchy;
    private InheritanceResolver resolver;

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        overrides = mock(InheritanceOverrideRepository.class);
        hierarchy = mock(OrgHierarchyRepository.class);
        resolver = new InheritanceResolver(hierarchy, assetVersions, overrides, List.of());
        when(hierarchy.findResolutionAncestorsAndSelf(TENANT_ID, "hospital-a"))
            .thenReturn(List.of(
                org("group-a", null, GROUP_PATH, OrgLevel.REGION),
                org("hospital-a", "group-a", HOSPITAL_PATH, OrgLevel.FACILITY)));
        when(overrides.findByTenantIdAndOrgPathInAndLifecycleStatusIn(
                eq(TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(List.of());
    }

    @Test
    void resolvesManyAssetsWithOneHierarchyReadOneOverrideReadAndTwoVersionReads() {
        List<VersionedAssetIdentity> identities = new ArrayList<>();
        List<AssetVersion> platformVersions = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            VersionedAssetIdentity identity =
                new VersionedAssetIdentity(VersionedAssetType.RULE, "RULE.BATCH." + i);
            identities.add(identity);
            platformVersions.add(version(
                PlatformAuthority.PLATFORM_TENANT_ID,
                identity,
                "platform-" + i,
                PlatformAuthority.PLATFORM_ORG_PATH));
        }
        when(assetVersions.findByTenantIdAndAssetIdentityInAndStatusIn(
                eq(TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(List.of());
        when(assetVersions.findByTenantIdAndAssetIdentityInAndStatusIn(
                eq(PlatformAuthority.PLATFORM_TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(platformVersions);

        List<BatchResolvedAsset> resolved = resolver.resolveBatch(new InheritanceBatchResolveQuery(
            TENANT_ID,
            identities,
            List.of("ALL"),
            "hospital-a",
            EFFECTIVE_AT));

        assertThat(resolved).hasSize(24);
        assertThat(resolved).allSatisfy(item -> {
            assertThat(item.added()).isFalse();
            assertThat(item.resolution().sourceTier()).isEqualTo(SourceTier.PLATFORM);
        });
        verify(hierarchy, times(1)).findResolutionAncestorsAndSelf(TENANT_ID, "hospital-a");
        verify(overrides, times(1)).findByTenantIdAndOrgPathInAndLifecycleStatusIn(
            eq(TENANT_ID), anyCollection(), anyCollection());
        verify(assetVersions, times(2)).findByTenantIdAndAssetIdentityInAndStatusIn(
            org.mockito.ArgumentMatchers.anyString(), anyCollection(), anyCollection());
        verify(assetVersions, never()).findByTenantIdAndAssetTypeAndAssetIdentity(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString());
        verify(overrides, never()).findByTenantIdAndOverrideVersionId(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void filtersForeignTenantRowsEvenWhenRepositoryReturnsContaminatedCandidates() {
        VersionedAssetIdentity identity =
            new VersionedAssetIdentity(VersionedAssetType.RULE, "RULE.ISOLATION");
        AssetVersion foreign = version("tenant-B", identity, "foreign-v1", "/TENANT-B/HOSP-B");
        AssetVersion platform = version(
            PlatformAuthority.PLATFORM_TENANT_ID,
            identity,
            "platform-v1",
            PlatformAuthority.PLATFORM_ORG_PATH);
        InheritanceOverride foreignOverride = override(
            "tenant-B",
            identity,
            "foreign-v1",
            InheritanceOverrideMode.ADD,
            HOSPITAL_PATH);
        when(overrides.findByTenantIdAndOrgPathInAndLifecycleStatusIn(
                eq(TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(List.of(foreignOverride));
        when(assetVersions.findByTenantIdAndAssetIdentityInAndStatusIn(
                eq(TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(List.of(foreign));
        when(assetVersions.findByTenantIdAndAssetIdentityInAndStatusIn(
                eq(PlatformAuthority.PLATFORM_TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(List.of(platform, foreign));

        List<BatchResolvedAsset> resolved = resolver.resolveBatch(new InheritanceBatchResolveQuery(
            TENANT_ID,
            List.of(identity),
            List.of("ALL"),
            "hospital-a",
            EFFECTIVE_AT));

        assertThat(resolved).singleElement().satisfies(item -> {
            assertThat(item.identity()).isEqualTo(identity);
            assertThat(item.added()).isFalse();
            assertThat(item.resolution().version()).isEqualTo(platform);
            assertThat(item.resolution().sourceTier()).isEqualTo(SourceTier.PLATFORM);
        });
    }

    @Test
    void doesNotImplicitlyIncludeTenantAddAssetsOutsideDeclaredRuntimeList() {
        VersionedAssetIdentity declared =
            new VersionedAssetIdentity(VersionedAssetType.RULE, "RULE.BASELINE");
        VersionedAssetIdentity localAdd =
            new VersionedAssetIdentity(VersionedAssetType.PATHWAY, "PATH.LOCAL.ADD");
        VersionedAssetIdentity foreignAdd =
            new VersionedAssetIdentity(VersionedAssetType.PATHWAY, "PATH.FOREIGN.ADD");
        VersionedAssetIdentity anotherLocalAdd =
            new VersionedAssetIdentity(VersionedAssetType.PATHWAY, "PATH.ANOTHER.LOCAL.ADD");
        AssetVersion baseline = version(
            PlatformAuthority.PLATFORM_TENANT_ID,
            declared,
            "platform-baseline",
            PlatformAuthority.PLATFORM_ORG_PATH);
        AssetVersion local = version(TENANT_ID, localAdd, "local-v1", HOSPITAL_PATH);
        AssetVersion foreign = version("tenant-B", foreignAdd, "foreign-v1", HOSPITAL_PATH);
        AssetVersion anotherLocal = version(TENANT_ID, anotherLocalAdd, "another-local-v1", HOSPITAL_PATH);
        when(overrides.findByTenantIdAndOrgPathInAndLifecycleStatusIn(
                eq(TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(List.of(
                override(TENANT_ID, localAdd, "local-v1", InheritanceOverrideMode.ADD, HOSPITAL_PATH),
                override(TENANT_ID, anotherLocalAdd, "another-local-v1", InheritanceOverrideMode.ADD, HOSPITAL_PATH),
                override("tenant-B", foreignAdd, "foreign-v1", InheritanceOverrideMode.ADD, HOSPITAL_PATH)));
        when(assetVersions.findByTenantIdAndAssetIdentityInAndStatusIn(
                eq(TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(List.of(local, anotherLocal, foreign));
        when(assetVersions.findByTenantIdAndAssetIdentityInAndStatusIn(
                eq(PlatformAuthority.PLATFORM_TENANT_ID), anyCollection(), anyCollection()))
            .thenReturn(List.of(baseline));

        List<BatchResolvedAsset> resolved = resolver.resolveBatch(new InheritanceBatchResolveQuery(
            TENANT_ID,
            List.of(declared),
            List.of("PKG.CARDIO.2026.06", "ALL"),
            "hospital-a",
            EFFECTIVE_AT));

        assertThat(resolved).extracting(BatchResolvedAsset::identity)
            .containsExactly(declared);
        assertThat(resolved).noneMatch(item ->
            item.identity().equals(foreignAdd)
                || item.identity().equals(localAdd)
                || item.identity().equals(anotherLocalAdd));
    }

    private OrgUnit org(String id, String parentId, String path, OrgLevel level) {
        return new OrgUnit(
            id,
            parentId,
            TENANT_ID,
            path,
            level,
            id,
            id,
            null,
            level == OrgLevel.FACILITY ? OrgFacilityType.HOSPITAL : null,
            null,
            OrgUnitStatus.ACTIVE,
            EFFECTIVE_AT,
            "system",
            EFFECTIVE_AT,
            "system");
    }

    private AssetVersion version(
            String tenantId,
            VersionedAssetIdentity identity,
            String versionId,
            String orgPath) {
        return new AssetVersion(
            null,
            versionId,
            tenantId,
            identity.assetType(),
            identity.assetIdentity(),
            "1",
            orgPath,
            "ALL",
            "hash-" + versionId,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            identity.assetIdentity() + "|" + orgPath + "|ALL",
            "test",
            EFFECTIVE_AT.minusSeconds(60),
            null,
            EFFECTIVE_AT.minusSeconds(60),
            "tester",
            EFFECTIVE_AT.minusSeconds(60),
            "tester",
            "trace-batch");
    }

    private InheritanceOverride override(
            String tenantId,
            VersionedAssetIdentity identity,
            String overrideVersionId,
            InheritanceOverrideMode mode,
            String orgPath) {
        return new InheritanceOverride(
            null,
            "io-" + identity.assetIdentity(),
            tenantId,
            identity.assetType(),
            identity.assetIdentity(),
            null,
            overrideVersionId,
            mode,
            InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.ACTIVE,
            orgPath,
            "ALL",
            "批量解析测试差异",
            "批量解析测试原因",
            "测试作用域",
            EFFECTIVE_AT.minusSeconds(60),
            "tester",
            EFFECTIVE_AT.minusSeconds(60),
            "tester",
            "trace-batch");
    }
}
