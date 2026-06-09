package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.context.OrgLevel;

class InheritanceResolverEffectiveScopeTest {

    private AssetVersionRepository assetVersions;
    private InheritanceOverrideRepository overrideRepository;
    private OrgHierarchyRepository hierarchy;
    private InheritanceResolver resolver;

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        overrideRepository = mock(InheritanceOverrideRepository.class);
        hierarchy = mock(OrgHierarchyRepository.class);
        resolver = new InheritanceResolver(
            hierarchy,
            assetVersions,
            overrideRepository,
            List.of(),
            mock(AssetDependencyRepository.class));
        when(hierarchy.findAncestorsAndSelf("tenant-A", "dept-1"))
            .thenReturn(List.of(
                org("hospital-1", "/TENANT-A/HOSPITAL-1", OrgLevel.FACILITY),
                org("dept-1", "/TENANT-A/HOSPITAL-1/DEPT-1", OrgLevel.DEPARTMENT)));
    }

    @Test
    void resolvesMostSpecificApplicableScopeAtRequestedTime() {
        AssetVersion general = version(
            "av-general", "ALL", AssetVersionStatus.PUBLISHED,
            "2026-01-01T00:00:00Z", null);
        AssetVersion specialty = version(
            "av-specialty", "specialty=AF", AssetVersionStatus.PUBLISHED,
            "2026-02-01T00:00:00Z", null);
        AssetVersion scenario = version(
            "av-scenario", "specialty=AF;scenario=S16", AssetVersionStatus.PUBLISHED,
            "2026-03-01T00:00:00Z", null);
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.AF"))
            .thenReturn(List.of(general, specialty, scenario));

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.AF",
            "specialty=AF;scenario=S16;setting=ED",
            "dept-1",
            Instant.parse("2026-06-01T00:00:00Z")));

        assertThat(resolved.version()).isEqualTo(scenario);
        assertThat(resolved.sourceOrgPath()).isEqualTo("/TENANT-A/HOSPITAL-1");
        assertThat(resolved.inherited()).isTrue();
    }

    @Test
    void historicalResolutionUsesVersionWhoseEffectiveWindowContainsRequestedTime() {
        AssetVersion historical = version(
            "av-history", "specialty=AF", AssetVersionStatus.DEPRECATED,
            "2026-01-01T00:00:00Z", "2026-03-01T00:00:00Z");
        AssetVersion current = version(
            "av-current", "specialty=AF", AssetVersionStatus.PUBLISHED,
            "2026-03-01T00:00:00Z", null);
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.AF"))
            .thenReturn(List.of(current, historical));

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.AF",
            "specialty=AF;scenario=S16",
            "dept-1",
            Instant.parse("2026-02-01T00:00:00Z")));

        assertThat(resolved.version()).isEqualTo(historical);
    }

    @Test
    void explicitEffectiveStartWinsOverUndatedVersionAtSameSpecificity() {
        AssetVersion undated = version(
            "av-undated", "specialty=AF", AssetVersionStatus.PUBLISHED,
            null, null);
        AssetVersion dated = version(
            "av-dated", "specialty=AF", AssetVersionStatus.PUBLISHED,
            "2026-03-01T00:00:00Z", null);
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.AF"))
            .thenReturn(List.of(undated, dated));

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.AF",
            "specialty=AF",
            "dept-1",
            Instant.parse("2026-06-01T00:00:00Z")));

        assertThat(resolved.version()).isEqualTo(dated);
    }

    @Test
    void historicalResolutionHonorsDisableOverrideBeforeItsRetirement() {
        InheritanceOverride retiredDisable = new InheritanceOverride(
            1L,
            "io-retired-disable",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.AF",
            "av-platform",
            null,
            InheritanceOverrideMode.DISABLE,
            InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.RETIRED,
            "/TENANT-A/HOSPITAL-1/DEPT-1",
            "specialty=AF",
            "历史停用",
            "历史验证",
            "房颤专病",
            Instant.parse("2026-01-01T00:00:00Z"),
            "admin",
            Instant.parse("2026-03-01T00:00:00Z"),
            "admin",
            "trace-1");
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.AF"))
            .thenReturn(List.of());
        when(overrideRepository.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.AF"))
            .thenReturn(List.of(retiredDisable));

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.AF",
            "specialty=AF",
            "dept-1",
            Instant.parse("2026-02-01T00:00:00Z")));

        assertThat(resolved.disabled()).isTrue();
        assertThat(resolved.explanation().resolutionSummary()).contains("io-retired-disable");
    }

    private OrgUnit org(String id, String path, OrgLevel level) {
        return new OrgUnit(
            id,
            "hospital-1".equals(id) ? null : "hospital-1",
            "tenant-A",
            path,
            level,
            id,
            id,
            null,
            level == OrgLevel.FACILITY ? OrgFacilityType.HOSPITAL : null,
            null,
            OrgUnitStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"),
            "admin",
            Instant.parse("2026-01-01T00:00:00Z"),
            "admin");
    }

    private AssetVersion version(
            String versionId,
            String applicableScope,
            AssetVersionStatus status,
            String effectiveFrom,
            String effectiveTo) {
        String orgPath = "/TENANT-A/HOSPITAL-1";
        return new AssetVersion(
            1L,
            versionId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.AF",
            versionId,
            orgPath,
            applicableScope,
            versionId + "-content-hash",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            "RULE.AF|" + orgPath + "|" + applicableScope,
            "rule/RULE.AF",
            effectiveFrom == null ? null : Instant.parse(effectiveFrom),
            effectiveTo == null ? null : Instant.parse(effectiveTo),
            Instant.parse("2026-01-01T00:00:00Z"),
            "admin",
            Instant.parse("2026-01-01T00:00:00Z"),
            "admin",
            "trace-1");
    }

}
