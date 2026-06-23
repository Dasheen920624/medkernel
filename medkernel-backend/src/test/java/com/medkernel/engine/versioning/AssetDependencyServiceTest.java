package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class AssetDependencyServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-04T09:00:00Z"), ZoneOffset.UTC);
    private static final String HOSP_PATH = "/TENANT-A/GROUP-A/HOSP-A";

    private AssetDependencyRepository dependencies;
    private AssetVersionRepository assetVersions;
    private AssetDependencyService service;

    @BeforeEach
    void setUp() {
        dependencies = mock(AssetDependencyRepository.class);
        assetVersions = mock(AssetVersionRepository.class);
        service = new AssetDependencyService(dependencies, assetVersions, CLOCK);
    }

    @Test
    void registersDependencyDeclarationsAsVersionScopedGraphEdges() {
        AssetVersion owner = ruleVersion("av-rule-v1", "RULE.ANEMIA", AssetVersionStatus.DRAFT);
        when(dependencies.save(any(AssetDependency.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerDependencies(owner, List.of(new AssetDependencyDeclaration(
            VersionedAssetType.TERMINOLOGY,
            "TERMINOLOGY.LOINC.718-7",
            "2.0.0",
            "2.9.9",
            AssetDependencyKind.TERMINOLOGY
        )), "author-1", "trace-dep");

        verify(dependencies).deleteByTenantIdAndAssetTypeAndAssetIdentityAndVersionId(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANEMIA", "av-rule-v1");
        verify(dependencies).save(org.mockito.ArgumentMatchers.argThat(edge ->
            edge.versionId().equals("av-rule-v1")
                && edge.assetIdentity().equals("RULE.ANEMIA")
                && edge.dependsOnAssetType() == VersionedAssetType.TERMINOLOGY
                && edge.dependsOnIdentity().equals("TERMINOLOGY.LOINC.718-7")
                && edge.minVersionNo().equals("2.0.0")
                && edge.maxVersionNo().equals("2.9.9")
                && edge.dependencyKind() == AssetDependencyKind.TERMINOLOGY
        ));
    }

    @Test
    void rejectsReleaseWhenDeclaredDependencyCannotResolveInTargetScopeOrPlatformBaseline() {
        AssetVersion owner = ruleVersion("av-rule-v1", "RULE.ANEMIA", AssetVersionStatus.PUBLISHED);
        AssetDependency edge = dependency(owner, VersionedAssetType.TERMINOLOGY, "TERMINOLOGY.LOINC.718-7", "2.0.0", null);
        when(dependencies.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANEMIA", "av-rule-v1"
        )).thenReturn(List.of(edge));

        assertThatThrownBy(() -> service.assertDependenciesResolvable(owner))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("引用完整性")
            .hasMessageContaining("TERMINOLOGY.LOINC.718-7")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void acceptsReleaseWhenDependencyResolvesToCompatiblePlatformBaseline() {
        AssetVersion owner = ruleVersion("av-rule-v1", "RULE.ANEMIA", AssetVersionStatus.PUBLISHED);
        AssetDependency edge = dependency(owner, VersionedAssetType.TERMINOLOGY, "TERMINOLOGY.LOINC.718-7", "2.0.0", "2.9.9");
        AssetVersion platformTerm = terminologyVersion(
            "av-term-platform", PlatformAuthority.PLATFORM_TENANT_ID,
            PlatformAuthority.PLATFORM_ORG_PATH, "TERMINOLOGY.LOINC.718-7", "2.3.0");
        when(dependencies.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
            "tenant-A", VersionedAssetType.RULE, "RULE.ANEMIA", "av-rule-v1"
        )).thenReturn(List.of(edge));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            PlatformAuthority.PLATFORM_TENANT_ID,
            VersionedAssetType.TERMINOLOGY,
            "TERMINOLOGY.LOINC.718-7",
            AssetVersionStatus.PUBLISHED
        )).thenReturn(List.of(platformTerm));

        assertThatCode(() -> service.assertDependenciesResolvable(owner)).doesNotThrowAnyException();
    }

    @Test
    void resolvesRuleDependencyByStableIdentityWhenPathwayAndRuleScopesDiffer() {
        AssetVersion pathway = version(
            "av-path-v1",
            "tenant-A",
            VersionedAssetType.PATHWAY,
            "PATHWAY.STROKE",
            "1.0.0",
            HOSP_PATH,
            AssetVersionStatus.PUBLISHED,
            "disease:I63"
        );
        AssetVersion rule = version(
            "av-rule-v1",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.STROKE.ADMISSION",
            "1.0.0",
            HOSP_PATH,
            AssetVersionStatus.PUBLISHED,
            "PKG.STROKE@1.0.0"
        );
        AssetDependency edge = new AssetDependency(
            1L,
            "dep-path-rule",
            pathway.tenantId(),
            pathway.assetType(),
            pathway.assetIdentity(),
            pathway.versionId(),
            VersionedAssetType.RULE,
            rule.assetIdentity(),
            null,
            null,
            AssetDependencyKind.RULE,
            CLOCK.instant(),
            "author-1",
            CLOCK.instant(),
            "author-1",
            "trace-dep"
        );
        when(dependencies.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
            pathway.tenantId(), pathway.assetType(), pathway.assetIdentity(), pathway.versionId()
        )).thenReturn(List.of(edge));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.RULE, "RULE.STROKE.ADMISSION", AssetVersionStatus.PUBLISHED
        )).thenReturn(List.of(rule));

        assertThatCode(() -> service.assertDependenciesResolvable(pathway)).doesNotThrowAnyException();
    }

    @Test
    void rejectsDisableWhenPublishedDependentWouldBeLeftDangling() {
        AssetVersion dependentRule = ruleVersion("av-rule-active", "RULE.ANEMIA", AssetVersionStatus.PUBLISHED);
        AssetDependency edge = dependency(
            dependentRule, VersionedAssetType.TERMINOLOGY, "TERMINOLOGY.LOINC.718-7", null, null);
        when(dependencies.findByTenantIdAndDependsOnAssetTypeAndDependsOnIdentity(
            "tenant-A", VersionedAssetType.TERMINOLOGY, "TERMINOLOGY.LOINC.718-7"
        )).thenReturn(List.of(edge));
        when(assetVersions.findByVersionIdAndTenantId("av-rule-active", "tenant-A"))
            .thenReturn(Optional.of(dependentRule));

        assertThatThrownBy(() -> service.assertDisableAllowed(
            "tenant-A", VersionedAssetType.TERMINOLOGY, "TERMINOLOGY.LOINC.718-7", HOSP_PATH, "adult|inpatient"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("引用完整性")
            .hasMessageContaining("RULE.ANEMIA")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void ignoresRetiredDependentWhenCheckingDisableDanglingReferences() {
        AssetVersion retiredRule = ruleVersion("av-rule-retired", "RULE.ANEMIA", AssetVersionStatus.WITHDRAWN);
        AssetDependency edge = dependency(
            retiredRule, VersionedAssetType.TERMINOLOGY, "TERMINOLOGY.LOINC.718-7", null, null);
        when(dependencies.findByTenantIdAndDependsOnAssetTypeAndDependsOnIdentity(
            "tenant-A", VersionedAssetType.TERMINOLOGY, "TERMINOLOGY.LOINC.718-7"
        )).thenReturn(List.of(edge));
        when(assetVersions.findByVersionIdAndTenantId("av-rule-retired", "tenant-A"))
            .thenReturn(Optional.of(retiredRule));

        assertThatCode(() -> service.assertDisableAllowed(
            "tenant-A", VersionedAssetType.TERMINOLOGY, "TERMINOLOGY.LOINC.718-7", HOSP_PATH, "adult|inpatient"))
            .doesNotThrowAnyException();
        verify(assetVersions).findByVersionIdAndTenantId("av-rule-retired", "tenant-A");
    }

    private AssetDependency dependency(
            AssetVersion owner,
            VersionedAssetType dependsOnType,
            String dependsOnIdentity,
            String minVersion,
            String maxVersion) {
        return new AssetDependency(
            1L,
            "dep-" + owner.versionId(),
            owner.tenantId(),
            owner.assetType(),
            owner.assetIdentity(),
            owner.versionId(),
            dependsOnType,
            dependsOnIdentity,
            minVersion,
            maxVersion,
            AssetDependencyKind.TERMINOLOGY,
            CLOCK.instant(),
            "author-1",
            CLOCK.instant(),
            "author-1",
            "trace-dep"
        );
    }

    private AssetVersion ruleVersion(String versionId, String identity, AssetVersionStatus status) {
        return version(versionId, "tenant-A", VersionedAssetType.RULE, identity, "1.0.0", HOSP_PATH, status);
    }

    private AssetVersion terminologyVersion(
            String versionId,
            String tenantId,
            String orgPath,
            String identity,
            String versionNo) {
        return version(versionId, tenantId, VersionedAssetType.TERMINOLOGY, identity, versionNo, orgPath,
            AssetVersionStatus.PUBLISHED);
    }

    private AssetVersion version(
            String versionId,
            String tenantId,
            VersionedAssetType assetType,
            String identity,
            String versionNo,
            String orgPath,
            AssetVersionStatus status) {
        return version(
            versionId, tenantId, assetType, identity, versionNo, orgPath, status, "adult|inpatient");
    }

    private AssetVersion version(
            String versionId,
            String tenantId,
            VersionedAssetType assetType,
            String identity,
            String versionNo,
            String orgPath,
            AssetVersionStatus status,
            String applicableScope) {
        return new AssetVersion(
            1L,
            versionId,
            tenantId,
            assetType,
            identity,
            versionNo,
            orgPath,
            applicableScope,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            status == AssetVersionStatus.PUBLISHED
                ? identity + "|" + orgPath + "|" + applicableScope
                : "version:" + versionId,
            assetType.name().toLowerCase() + "/" + identity,
            null,
            null,
            CLOCK.instant(),
            "author-1",
            CLOCK.instant(),
            "author-1",
            "trace-dep"
        );
    }
}
