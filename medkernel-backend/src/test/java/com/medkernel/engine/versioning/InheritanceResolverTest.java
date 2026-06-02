package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.context.OrgLevel;

class InheritanceResolverTest {

    private AssetVersionRepository assetVersions;
    private InheritanceOverrideRepository overrides;
    private OrgHierarchyRepository hierarchy;
    private InheritanceResolver resolver;

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        overrides = mock(InheritanceOverrideRepository.class);
        hierarchy = mock(OrgHierarchyRepository.class);
        resolver = new InheritanceResolver(hierarchy, assetVersions, overrides);
    }

    @Test
    void resolvesNearestLocalOverrideAndExplainsDiffReason() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospitalA = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.HOSPITAL, "HOSP-A");
        OrgUnit hospitalB = org("hospital-b", "group-1", "/TENANT-A/GROUP-A/HOSP-B", OrgLevel.HOSPITAL, "HOSP-B");
        AssetVersion groupV1 = version(
            "av-group-v1",
            "1.0.0",
            group.orgPath(),
            AssetVersionSafetyPolicy.NORMAL
        );
        AssetVersion hospitalOverride = version(
            "av-hospital-v1p",
            "1.0.0-hosp-a",
            hospitalA.orgPath(),
            AssetVersionSafetyPolicy.NORMAL
        );
        InheritanceOverride explanation = override(
            "io-hospital-a",
            groupV1.versionId(),
            hospitalOverride.versionId(),
            hospitalA.orgPath(),
            "本院 D-二聚体阈值按检验参考区间上调",
            "医院检验科 2026 年参考区间已更新",
            "仅 HOSP-A 成人住院"
        );

        when(hierarchy.findAncestorsAndSelf("tenant-A", hospitalA.id())).thenReturn(List.of(group, hospitalA));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospitalB.id())).thenReturn(List.of(group, hospitalB));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + hospitalA.orgPath() + "|adult|inpatient",
            AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(hospitalOverride));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + group.orgPath() + "|adult|inpatient",
            AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(groupV1));
        when(overrides.findByTenantIdAndOverrideVersionId("tenant-A", hospitalOverride.versionId()))
            .thenReturn(Optional.of(explanation));
        when(overrides.findByTenantIdAndOverrideVersionId("tenant-A", groupV1.versionId()))
            .thenReturn(Optional.empty());

        ResolvedAssetVersion resolvedForHospitalA = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "adult|inpatient",
            hospitalA.id()
        ));
        ResolvedAssetVersion resolvedForHospitalB = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "adult|inpatient",
            hospitalB.id()
        ));

        assertThat(resolvedForHospitalA.version()).isEqualTo(hospitalOverride);
        assertThat(resolvedForHospitalA.inherited()).isFalse();
        assertThat(resolvedForHospitalA.overridden()).isTrue();
        assertThat(resolvedForHospitalA.explanation().inheritancePath())
            .containsExactly(group.orgPath(), hospitalA.orgPath());
        assertThat(resolvedForHospitalA.explanation().diffSummary())
            .isEqualTo("本院 D-二聚体阈值按检验参考区间上调");
        assertThat(resolvedForHospitalA.explanation().overrideReason())
            .isEqualTo("医院检验科 2026 年参考区间已更新");
        assertThat(resolvedForHospitalA.explanation().impactScope())
            .isEqualTo("仅 HOSP-A 成人住院");

        assertThat(resolvedForHospitalB.version()).isEqualTo(groupV1);
        assertThat(resolvedForHospitalB.inherited()).isTrue();
        assertThat(resolvedForHospitalB.overridden()).isFalse();
        assertThat(resolvedForHospitalB.sourceOrgPath()).isEqualTo(group.orgPath());
        assertThat(resolvedForHospitalB.explanation().resolutionSummary())
            .contains("继承上级组织版本");
    }

    private OrgUnit org(String id, String parentId, String orgPath, OrgLevel level, String code) {
        return new OrgUnit(
            id,
            parentId,
            "tenant-A",
            orgPath,
            level,
            code,
            code,
            null,
            null,
            OrgUnitStatus.ACTIVE,
            Instant.parse("2026-06-03T08:00:00Z"),
            "admin-1",
            Instant.parse("2026-06-03T08:00:00Z"),
            "admin-1"
        );
    }

    private AssetVersion version(String versionId, String versionNo, String orgPath, AssetVersionSafetyPolicy safetyPolicy) {
        Instant now = Instant.parse("2026-06-03T08:00:00Z");
        return new AssetVersion(
            1L,
            versionId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            orgPath,
            "adult|inpatient",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            safetyPolicy,
            AssetVersionStatus.ACTIVE,
            "RULE.VTE.RISK|" + orgPath + "|adult|inpatient",
            "rule/RULE.VTE.RISK",
            null,
            null,
            now,
            "publisher-1",
            now,
            "publisher-1",
            "trace-sys04"
        );
    }

    private InheritanceOverride override(
            String overrideId,
            String inheritedVersionId,
            String overrideVersionId,
            String orgPath,
            String diffSummary,
            String overrideReason,
            String impactScope) {
        Instant now = Instant.parse("2026-06-03T08:00:00Z");
        return new InheritanceOverride(
            1L,
            overrideId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inheritedVersionId,
            overrideVersionId,
            InheritanceOverrideMode.REPLACE,
            orgPath,
            "adult|inpatient",
            diffSummary,
            overrideReason,
            impactScope,
            now,
            "publisher-1",
            now,
            "publisher-1",
            "trace-sys04"
        );
    }
}
