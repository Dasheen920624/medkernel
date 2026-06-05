package com.medkernel.engine.versioning;

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

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgLevel;

class InheritanceResolverTest {

    private static final String GROUP_PATH = "/TENANT-A/GROUP-A";
    private static final String HOSP_PATH = "/TENANT-A/GROUP-A/HOSP-A";
    private static final String DEPT_PATH = "/TENANT-A/GROUP-A/HOSP-A/DEPT-X";
    private static final String HASH_BASELINE = "content-hash-baseline";
    private static final String HASH_OVERRIDE = "content-hash-override";

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

    @Test
    void exclusiveOverrideDoesNotPropagateToDescendants() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.HOSPITAL, "HOSP-A");
        OrgUnit department = org("dept-x", "hospital-a", "/TENANT-A/GROUP-A/HOSP-A/DEPT-X", OrgLevel.DEPARTMENT, "DEPT-X");
        AssetVersion groupV1 = version("av-group-v1", "1.0.0", group.orgPath(), AssetVersionSafetyPolicy.NORMAL);
        AssetVersion hospitalExclusive = version(
            "av-hosp-excl", "1.0.0-hosp-a", hospital.orgPath(), AssetVersionSafetyPolicy.NORMAL);
        InheritanceOverride exclusiveOverride = override(
            "io-hosp-excl",
            groupV1.versionId(),
            hospitalExclusive.versionId(),
            hospital.orgPath(),
            "仅本院镇痛路径",
            "本院专用，不下沉科室",
            "仅 HOSP-A 本级",
            InheritancePropagation.EXCLUSIVE
        );

        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(hierarchy.findAncestorsAndSelf("tenant-A", department.id()))
            .thenReturn(List.of(group, hospital, department));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A", VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + department.orgPath() + "|adult|inpatient", AssetVersionStatus.ACTIVE
        )).thenReturn(List.of());
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A", VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + hospital.orgPath() + "|adult|inpatient", AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(hospitalExclusive));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A", VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + group.orgPath() + "|adult|inpatient", AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(groupV1));
        when(overrides.findByTenantIdAndOverrideVersionId("tenant-A", hospitalExclusive.versionId()))
            .thenReturn(Optional.of(exclusiveOverride));
        when(overrides.findByTenantIdAndOverrideVersionId("tenant-A", groupV1.versionId()))
            .thenReturn(Optional.empty());

        // 覆盖所在节点（医院本级）命中 EXCLUSIVE 版本
        ResolvedAssetVersion atHospital = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", hospital.id()));
        assertThat(atHospital.version()).isEqualTo(hospitalExclusive);
        assertThat(atHospital.overridden()).isTrue();
        assertThat(atHospital.inherited()).isFalse();

        // 下级科室不继承祖先 EXCLUSIVE 覆盖，回退到集团版本
        ResolvedAssetVersion atDepartment = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", department.id()));
        assertThat(atDepartment.version()).isEqualTo(groupV1);
        assertThat(atDepartment.inherited()).isTrue();
        assertThat(atDepartment.overridden()).isFalse();
        assertThat(atDepartment.sourceOrgPath()).isEqualTo(group.orgPath());
    }

    @Test
    void lockedBaselineIgnoresContentChangingReplaceAndInheritsLockedVersion() {
        AssetVersion groupLocked = version(
            "av-group-locked", "1.0.0", GROUP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.LOCKED, HASH_BASELINE);
        AssetVersion hospitalReplace = version(
            "av-hosp-replace", "1.0.0-hosp-a", HOSP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, HASH_OVERRIDE);
        InheritanceOverride record = override(
            "io-locked-replace", groupLocked.versionId(), hospitalReplace.versionId(),
            HOSP_PATH, "下调阈值", "本院诉求", "仅 HOSP-A");

        ResolvedAssetVersion resolved = resolveHospitalOverride(groupLocked, hospitalReplace, record);

        assertThat(resolved.version()).isEqualTo(groupLocked);
        assertThat(resolved.inherited()).isTrue();
        assertThat(resolved.overridden()).isFalse();
        assertThat(resolved.sourceOrgPath()).isEqualTo(GROUP_PATH);
        assertThat(resolved.explanation().resolutionSummary())
            .contains("平台安全锁定").contains("io-locked-replace");
    }

    @Test
    void redlineBaselineIgnoresSafetyDowngradingReplace() {
        AssetVersion groupRedline = version(
            "av-group-redline", "1.0.0", GROUP_PATH,
            AssetVersionSafetyPolicy.SAFETY_REDLINE, AssetVersionOverridePolicy.FREE, HASH_BASELINE);
        AssetVersion hospitalDowngrade = version(
            "av-hosp-downgrade", "1.0.0-hosp-a", HOSP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, HASH_OVERRIDE);
        InheritanceOverride record = override(
            "io-redline-downgrade", groupRedline.versionId(), hospitalDowngrade.versionId(),
            HOSP_PATH, "降级为提醒", "本院诉求", "仅 HOSP-A");

        ResolvedAssetVersion resolved = resolveHospitalOverride(groupRedline, hospitalDowngrade, record);

        assertThat(resolved.version()).isEqualTo(groupRedline);
        assertThat(resolved.inherited()).isTrue();
        assertThat(resolved.overridden()).isFalse();
        assertThat(resolved.explanation().resolutionSummary())
            .contains("平台安全锁定").contains("io-redline-downgrade");
    }

    @Test
    void redlineBaselineKeepsNonDowngradingReplace() {
        AssetVersion groupRedline = version(
            "av-group-redline2", "1.0.0", GROUP_PATH,
            AssetVersionSafetyPolicy.SAFETY_REDLINE, AssetVersionOverridePolicy.FREE, HASH_BASELINE);
        AssetVersion hospitalRedlineReplace = version(
            "av-hosp-redline", "1.0.0-hosp-a", HOSP_PATH,
            AssetVersionSafetyPolicy.SAFETY_REDLINE, AssetVersionOverridePolicy.FREE, HASH_OVERRIDE);
        InheritanceOverride record = override(
            "io-redline-lateral", groupRedline.versionId(), hospitalRedlineReplace.versionId(),
            HOSP_PATH, "更严阈值", "本院诉求", "仅 HOSP-A");

        ResolvedAssetVersion resolved = resolveHospitalOverride(groupRedline, hospitalRedlineReplace, record);

        assertThat(resolved.version()).isEqualTo(hospitalRedlineReplace);
        assertThat(resolved.inherited()).isFalse();
        assertThat(resolved.overridden()).isTrue();
    }

    @Test
    void lockedBaselineKeepsContentIdenticalReplace() {
        AssetVersion groupLocked = version(
            "av-group-locked2", "1.0.0", GROUP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.LOCKED, HASH_BASELINE);
        AssetVersion hospitalSame = version(
            "av-hosp-same", "1.0.0-hosp-a", HOSP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, HASH_BASELINE);
        InheritanceOverride record = override(
            "io-locked-noop", groupLocked.versionId(), hospitalSame.versionId(),
            HOSP_PATH, "无内容变更", "本院诉求", "仅 HOSP-A");

        ResolvedAssetVersion resolved = resolveHospitalOverride(groupLocked, hospitalSame, record);

        assertThat(resolved.version()).isEqualTo(hospitalSame);
        assertThat(resolved.overridden()).isTrue();
        assertThat(resolved.inherited()).isFalse();
    }

    @Test
    void lockedBaselineKeepsTighteningReplaceVouchedByDomainCheck() {
        SafetyMonotonicityCheck check = mock(SafetyMonotonicityCheck.class);
        when(check.supports(VersionedAssetType.RULE)).thenReturn(true);
        when(check.isAtLeastAsStrict(any(), any())).thenReturn(true);
        resolver = new InheritanceResolver(hierarchy, assetVersions, overrides, List.of(check));

        AssetVersion groupLocked = version(
            "av-group-locked3", "1.0.0", GROUP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.LOCKED, HASH_BASELINE);
        AssetVersion hospTighten = version(
            "av-hosp-tighten", "1.0.0-hosp-a", HOSP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, HASH_OVERRIDE);
        InheritanceOverride record = override(
            "io-locked-tighten", groupLocked.versionId(), hospTighten.versionId(),
            HOSP_PATH, "更严阈值", "经审核收紧", "仅 HOSP-A");

        ResolvedAssetVersion resolved = resolveHospitalOverride(groupLocked, hospTighten, record);

        assertThat(resolved.version()).isEqualTo(hospTighten);
        assertThat(resolved.overridden()).isTrue();
        assertThat(resolved.inherited()).isFalse();
    }

    @Test
    void lockedBaselineIgnoresRelaxingReplaceRejectedByDomainCheck() {
        SafetyMonotonicityCheck check = mock(SafetyMonotonicityCheck.class);
        when(check.supports(VersionedAssetType.RULE)).thenReturn(true);
        when(check.isAtLeastAsStrict(any(), any())).thenReturn(false);
        resolver = new InheritanceResolver(hierarchy, assetVersions, overrides, List.of(check));

        AssetVersion groupLocked = version(
            "av-group-locked4", "1.0.0", GROUP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.LOCKED, HASH_BASELINE);
        AssetVersion hospRelax = version(
            "av-hosp-relax", "1.0.0-hosp-a", HOSP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, HASH_OVERRIDE);
        InheritanceOverride record = override(
            "io-locked-relax", groupLocked.versionId(), hospRelax.versionId(),
            HOSP_PATH, "放宽阈值", "下级放宽", "仅 HOSP-A");

        ResolvedAssetVersion resolved = resolveHospitalOverride(groupLocked, hospRelax, record);

        assertThat(resolved.version()).isEqualTo(groupLocked);
        assertThat(resolved.inherited()).isTrue();
        assertThat(resolved.overridden()).isFalse();
        assertThat(resolved.explanation().resolutionSummary())
            .contains("平台安全锁定").contains("io-locked-relax");
    }

    @Test
    void disableOverrideAtTargetResolvesToDisabled() {
        OrgUnit group = org("group-1", null, GROUP_PATH, OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", HOSP_PATH, OrgLevel.HOSPITAL, "HOSP-A");
        AssetVersion groupV1 = version("av-group-v1", "1.0.0", GROUP_PATH, AssetVersionSafetyPolicy.NORMAL);
        InheritanceOverride disable = disableOverride(
            "io-disable-hosp", groupV1.versionId(), HOSP_PATH, InheritancePropagation.INHERITABLE);

        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        stubDisableAt(HOSP_PATH, disable);
        when(assetVersions.findByVersionIdAndTenantId(groupV1.versionId(), "tenant-A"))
            .thenReturn(Optional.of(groupV1));

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", hospital.id()));

        assertThat(resolved.disabled()).isTrue();
        assertThat(resolved.version()).isNull();
        assertThat(resolved.overridden()).isTrue();
        assertThat(resolved.sourceOrgPath()).isEqualTo(HOSP_PATH);
        assertThat(resolved.explanation().resolutionSummary()).contains("停用").contains("io-disable-hosp");
    }

    @Test
    void inheritableDisablePropagatesToDescendant() {
        OrgUnit group = org("group-1", null, GROUP_PATH, OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", HOSP_PATH, OrgLevel.HOSPITAL, "HOSP-A");
        OrgUnit dept = org("dept-x", "hospital-a", DEPT_PATH, OrgLevel.DEPARTMENT, "DEPT-X");
        AssetVersion groupV1 = version("av-group-v1", "1.0.0", GROUP_PATH, AssetVersionSafetyPolicy.NORMAL);
        InheritanceOverride disable = disableOverride(
            "io-disable-hosp", groupV1.versionId(), HOSP_PATH, InheritancePropagation.INHERITABLE);

        when(hierarchy.findAncestorsAndSelf("tenant-A", dept.id()))
            .thenReturn(List.of(group, hospital, dept));
        stubDisableAt(HOSP_PATH, disable);
        when(assetVersions.findByVersionIdAndTenantId(groupV1.versionId(), "tenant-A"))
            .thenReturn(Optional.of(groupV1));

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", dept.id()));

        assertThat(resolved.disabled()).isTrue();
        assertThat(resolved.inherited()).isTrue();
        assertThat(resolved.sourceOrgPath()).isEqualTo(HOSP_PATH);
    }

    @Test
    void exclusiveDisableDoesNotPropagateButAppliesAtOwnNode() {
        OrgUnit group = org("group-1", null, GROUP_PATH, OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", HOSP_PATH, OrgLevel.HOSPITAL, "HOSP-A");
        OrgUnit dept = org("dept-x", "hospital-a", DEPT_PATH, OrgLevel.DEPARTMENT, "DEPT-X");
        AssetVersion groupV1 = version("av-group-v1", "1.0.0", GROUP_PATH, AssetVersionSafetyPolicy.NORMAL);
        InheritanceOverride disable = disableOverride(
            "io-disable-excl", groupV1.versionId(), HOSP_PATH, InheritancePropagation.EXCLUSIVE);

        when(hierarchy.findAncestorsAndSelf("tenant-A", dept.id()))
            .thenReturn(List.of(group, hospital, dept));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A", VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + GROUP_PATH + "|adult|inpatient", AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(groupV1));
        stubDisableAt(HOSP_PATH, disable);
        when(assetVersions.findByVersionIdAndTenantId(groupV1.versionId(), "tenant-A"))
            .thenReturn(Optional.of(groupV1));

        ResolvedAssetVersion atDept = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", dept.id()));
        assertThat(atDept.disabled()).isFalse();
        assertThat(atDept.version()).isEqualTo(groupV1);
        assertThat(atDept.inherited()).isTrue();

        ResolvedAssetVersion atHospital = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", hospital.id()));
        assertThat(atHospital.disabled()).isTrue();
        assertThat(atHospital.sourceOrgPath()).isEqualTo(HOSP_PATH);
    }

    @Test
    void disableOfLockedBaselineIsRefusedAndInheritsLockedVersion() {
        OrgUnit group = org("group-1", null, GROUP_PATH, OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", HOSP_PATH, OrgLevel.HOSPITAL, "HOSP-A");
        AssetVersion groupLocked = version(
            "av-group-locked", "1.0.0", GROUP_PATH,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.LOCKED, HASH_BASELINE);
        InheritanceOverride disable = disableOverride(
            "io-disable-locked", groupLocked.versionId(), HOSP_PATH, InheritancePropagation.INHERITABLE);

        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A", VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + GROUP_PATH + "|adult|inpatient", AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(groupLocked));
        stubDisableAt(HOSP_PATH, disable);
        when(assetVersions.findByVersionIdAndTenantId(groupLocked.versionId(), "tenant-A"))
            .thenReturn(Optional.of(groupLocked));

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", hospital.id()));

        assertThat(resolved.disabled()).isFalse();
        assertThat(resolved.version()).isEqualTo(groupLocked);
        assertThat(resolved.inherited()).isTrue();
        assertThat(resolved.explanation().resolutionSummary())
            .contains("平台安全锁定").contains("io-disable-locked");
    }

    @Test
    void unmatchedTenantInheritsPlatformBaselineWithPlatformTier() {
        OrgUnit group = org("group-1", null, GROUP_PATH, OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", HOSP_PATH, OrgLevel.HOSPITAL, "HOSP-A");
        AssetVersion platformV0 = platformVersion("av-platform-v0", "1.0.0");

        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        stubPlatformBaseline(platformV0);

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", hospital.id()));

        assertThat(resolved.version()).isEqualTo(platformV0);
        assertThat(resolved.sourceTier()).isEqualTo(SourceTier.PLATFORM);
        assertThat(resolved.inherited()).isTrue();
        assertThat(resolved.overridden()).isFalse();
        assertThat(resolved.disabled()).isFalse();
        assertThat(resolved.sourceOrgPath()).isEqualTo(PlatformAuthority.PLATFORM_ORG_PATH);
        assertThat(resolved.explanation().resolutionSummary()).contains("平台权威基线");
        assertThat(resolved.explanation().inheritancePath())
            .containsExactly(PlatformAuthority.PLATFORM_ORG_PATH, GROUP_PATH, HOSP_PATH);
    }

    @Test
    void tenantOwnVersionShadowsPlatformBaselineWithOrgTier() {
        OrgUnit group = org("group-1", null, GROUP_PATH, OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", HOSP_PATH, OrgLevel.HOSPITAL, "HOSP-A");
        AssetVersion hospitalOwn = version("av-hosp-own", "1.0.0-hosp-a", HOSP_PATH, AssetVersionSafetyPolicy.NORMAL);
        AssetVersion platformV0 = platformVersion("av-platform-v0", "9.9.9");

        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A", VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + HOSP_PATH + "|adult|inpatient", AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(hospitalOwn));
        when(overrides.findByTenantIdAndOverrideVersionId("tenant-A", hospitalOwn.versionId()))
            .thenReturn(Optional.empty());
        // 即便平台存在基线，本租户本级版本应遮蔽平台、不回退平台（引用而非复制）
        stubPlatformBaseline(platformV0);

        ResolvedAssetVersion resolved = resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", hospital.id()));

        assertThat(resolved.version()).isEqualTo(hospitalOwn);
        assertThat(resolved.sourceTier()).isEqualTo(SourceTier.ORG);
        assertThat(resolved.overridden()).isFalse();
        assertThat(resolved.inherited()).isFalse();
    }

    @Test
    void missingPlatformBaselineResolvesNotFoundHonestly() {
        OrgUnit group = org("group-1", null, GROUP_PATH, OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", HOSP_PATH, OrgLevel.HOSPITAL, "HOSP-A");
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        // 组织闭包与平台均无 ACTIVE 基线（默认空）：诚实降级 NOT_FOUND，不伪造

        assertThatThrownBy(() -> resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", hospital.id())))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("未找到");
    }

    private void stubPlatformBaseline(AssetVersion platformVersion) {
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            PlatformAuthority.PLATFORM_TENANT_ID,
            VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + PlatformAuthority.PLATFORM_ORG_PATH + "|adult|inpatient",
            AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(platformVersion));
    }

    private AssetVersion platformVersion(String versionId, String versionNo) {
        Instant now = Instant.parse("2026-06-03T08:00:00Z");
        return new AssetVersion(
            1L,
            versionId,
            PlatformAuthority.PLATFORM_TENANT_ID,
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            PlatformAuthority.PLATFORM_ORG_PATH,
            "adult|inpatient",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.ACTIVE,
            "RULE.VTE.RISK|" + PlatformAuthority.PLATFORM_ORG_PATH + "|adult|inpatient",
            "rule/RULE.VTE.RISK",
            null,
            null,
            now,
            "platform-publisher",
            now,
            "platform-publisher",
            "trace-platform"
        );
    }

    private void stubDisableAt(String orgPath, InheritanceOverride disable) {
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndOverrideMode(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", orgPath, "adult|inpatient",
            InheritanceOverrideMode.DISABLE
        )).thenReturn(List.of(disable));
    }

    private InheritanceOverride disableOverride(
            String overrideId, String inheritedVersionId, String orgPath, InheritancePropagation propagation) {
        Instant now = Instant.parse("2026-06-03T08:00:00Z");
        return new InheritanceOverride(
            1L,
            overrideId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inheritedVersionId,
            null,
            InheritanceOverrideMode.DISABLE,
            propagation,
            orgPath,
            "adult|inpatient",
            "本机构停用该资产",
            "本机构不适用",
            "仅 " + orgPath,
            now,
            "publisher-1",
            now,
            "publisher-1",
            "trace-sys04"
        );
    }

    /**
     * 组装“集团基线 + 分院 REPLACE 覆盖”两级闭包并在分院解析，复用于安全护栏场景。
     */
    private ResolvedAssetVersion resolveHospitalOverride(
            AssetVersion groupBaseline,
            AssetVersion hospitalOverrideVersion,
            InheritanceOverride overrideRecord) {
        OrgUnit group = org("group-1", null, GROUP_PATH, OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", HOSP_PATH, OrgLevel.HOSPITAL, "HOSP-A");
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A", VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + HOSP_PATH + "|adult|inpatient", AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(hospitalOverrideVersion));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A", VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + GROUP_PATH + "|adult|inpatient", AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(groupBaseline));
        when(overrides.findByTenantIdAndOverrideVersionId("tenant-A", hospitalOverrideVersion.versionId()))
            .thenReturn(Optional.of(overrideRecord));
        when(overrides.findByTenantIdAndOverrideVersionId("tenant-A", groupBaseline.versionId()))
            .thenReturn(Optional.empty());
        when(assetVersions.findByVersionIdAndTenantId(groupBaseline.versionId(), "tenant-A"))
            .thenReturn(Optional.of(groupBaseline));
        return resolver.resolve(new InheritanceResolveQuery(
            "tenant-A", VersionedAssetType.RULE, "RULE.VTE.RISK", "adult|inpatient", hospital.id()));
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
        return version(
            versionId, versionNo, orgPath, safetyPolicy, AssetVersionOverridePolicy.FREE,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
    }

    private AssetVersion version(
            String versionId,
            String versionNo,
            String orgPath,
            AssetVersionSafetyPolicy safetyPolicy,
            AssetVersionOverridePolicy overridePolicy,
            String contentHash) {
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
            contentHash,
            safetyPolicy,
            overridePolicy,
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
        return override(overrideId, inheritedVersionId, overrideVersionId, orgPath,
            diffSummary, overrideReason, impactScope, InheritancePropagation.INHERITABLE);
    }

    private InheritanceOverride override(
            String overrideId,
            String inheritedVersionId,
            String overrideVersionId,
            String orgPath,
            String diffSummary,
            String overrideReason,
            String impactScope,
            InheritancePropagation propagation) {
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
            propagation,
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
