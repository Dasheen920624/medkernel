package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

class InheritanceOverrideServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T08:00:00Z"), ZoneOffset.UTC);

    private AssetVersionRepository assetVersions;
    private InheritanceOverrideRepository overrides;
    private OrgHierarchyRepository hierarchy;
    private PermissionEvaluator permissionEvaluator;
    private InheritanceOverrideService service;

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        overrides = mock(InheritanceOverrideRepository.class);
        hierarchy = mock(OrgHierarchyRepository.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        service = new InheritanceOverrideService(assetVersions, overrides, hierarchy, permissionEvaluator, CLOCK);
        when(permissionEvaluator.has(PermissionCode.TENANT_OVERRIDE)).thenReturn(true);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }

    @Test
    void registersLocalOverrideWithRequiredExplanation() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.REGION, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        AssetVersion inherited = version("av-group-v1", "1.0.0", group.orgPath(), AssetVersionSafetyPolicy.NORMAL);
        AssetVersion local = version("av-hospital-v1p", "1.0.0-hosp-a", hospital.orgPath(), AssetVersionSafetyPolicy.NORMAL);

        when(assetVersions.findByVersionIdAndTenantId(inherited.versionId(), "tenant-A")).thenReturn(Optional.of(inherited));
        when(assetVersions.findByVersionIdAndTenantId(local.versionId(), "tenant-A")).thenReturn(Optional.of(local));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(overrides.save(any(InheritanceOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InheritanceOverride saved = service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inherited.versionId(),
            local.versionId(),
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.REPLACE,
            "  本院 D-二聚体阈值按检验参考区间上调  ",
            "  医院检验科 2026 年参考区间已更新  ",
            "  仅 HOSP-A 成人住院  ",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.INHERITABLE
        ));

        assertThat(saved.overrideId()).matches("io-[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(saved.orgPath()).isEqualTo(hospital.orgPath());
        assertThat(saved.diffSummary()).isEqualTo("本院 D-二聚体阈值按检验参考区间上调");
        assertThat(saved.overrideReason()).isEqualTo("医院检验科 2026 年参考区间已更新");
        assertThat(saved.impactScope()).isEqualTo("仅 HOSP-A 成人住院");
        assertThat(saved.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(saved.propagation()).isEqualTo(InheritancePropagation.INHERITABLE);
        assertThat(saved.lifecycleStatus()).isEqualTo(InheritanceOverrideStatus.PUBLISHED);
    }

    @Test
    void registersExclusivePropagationWhenRequested() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.REGION, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        AssetVersion inherited = version("av-group-v1", "1.0.0", group.orgPath(), AssetVersionSafetyPolicy.NORMAL);
        AssetVersion local = version("av-hospital-v1p", "1.0.0-hosp-a", hospital.orgPath(), AssetVersionSafetyPolicy.NORMAL);

        when(assetVersions.findByVersionIdAndTenantId(inherited.versionId(), "tenant-A")).thenReturn(Optional.of(inherited));
        when(assetVersions.findByVersionIdAndTenantId(local.versionId(), "tenant-A")).thenReturn(Optional.of(local));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(overrides.save(any(InheritanceOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InheritanceOverride saved = service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inherited.versionId(),
            local.versionId(),
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.REPLACE,
            "仅本院镇痛路径，不下沉科室",
            "本院专用方案",
            "仅 HOSP-A 本级",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.EXCLUSIVE
        ));

        assertThat(saved.propagation()).isEqualTo(InheritancePropagation.EXCLUSIVE);
        assertThat(saved.lifecycleStatus()).isEqualTo(InheritanceOverrideStatus.PUBLISHED);
    }

    @Test
    void deniesOverrideWhenActorLacksTenantOverridePermission() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.REGION, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        AssetVersion inherited = version("av-group-v1", "1.0.0", group.orgPath(), AssetVersionSafetyPolicy.NORMAL);
        AssetVersion local = version("av-hospital-v1p", "1.0.0-hosp-a", hospital.orgPath(), AssetVersionSafetyPolicy.NORMAL);
        when(permissionEvaluator.has(PermissionCode.TENANT_OVERRIDE)).thenReturn(false);
        when(assetVersions.findByVersionIdAndTenantId(inherited.versionId(), "tenant-A")).thenReturn(Optional.of(inherited));
        when(assetVersions.findByVersionIdAndTenantId(local.versionId(), "tenant-A")).thenReturn(Optional.of(local));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));

        assertThatThrownBy(() -> service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inherited.versionId(),
            local.versionId(),
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.REPLACE,
            "本院阈值更严格",
            "本院检验参考区间更新",
            "HOSP-A",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.INHERITABLE
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("tenant.override")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(overrides, never()).save(any(InheritanceOverride.class));
    }

    @Test
    void deniesOverrideOutsideActorOrganizationClosure() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.REGION, "GROUP-A");
        OrgUnit hospitalA = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        OrgUnit hospitalB = org("hospital-b", "group-1", "/TENANT-A/GROUP-A/HOSP-B", OrgLevel.FACILITY, "HOSP-B");
        AssetVersion inherited = version("av-group-v1", "1.0.0", group.orgPath(), AssetVersionSafetyPolicy.NORMAL);
        AssetVersion local = version("av-hospital-b-v1p", "1.0.0-hosp-b", hospitalB.orgPath(), AssetVersionSafetyPolicy.NORMAL);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-sys04",
            new OrgScope("tenant-A", "group-1", "hospital-a", null, null, null, null),
            "publisher-1"));
        when(assetVersions.findByVersionIdAndTenantId(inherited.versionId(), "tenant-A")).thenReturn(Optional.of(inherited));
        when(assetVersions.findByVersionIdAndTenantId(local.versionId(), "tenant-A")).thenReturn(Optional.of(local));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospitalB.id())).thenReturn(List.of(group, hospitalB));
        when(hierarchy.isDescendant("tenant-A", hospitalA.id(), hospitalB.id())).thenReturn(false);

        assertThatThrownBy(() -> service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inherited.versionId(),
            local.versionId(),
            hospitalB.id(),
            "adult|inpatient",
            InheritanceOverrideMode.REPLACE,
            "跨院覆盖",
            "不应允许越权配置其他机构",
            "HOSP-B",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.INHERITABLE
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("自身组织闭包")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(overrides, never()).save(any(InheritanceOverride.class));
    }

    @Test
    void reviewPolicyOverrideIsRegisteredAsInReviewInsteadOfPublished() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.REGION, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        AssetVersion inherited = version(
            "av-group-v1", "1.0.0", group.orgPath(),
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.REVIEW);
        AssetVersion local = version("av-hospital-v1p", "1.0.0-hosp-a", hospital.orgPath(), AssetVersionSafetyPolicy.NORMAL);

        when(assetVersions.findByVersionIdAndTenantId(inherited.versionId(), "tenant-A")).thenReturn(Optional.of(inherited));
        when(assetVersions.findByVersionIdAndTenantId(local.versionId(), "tenant-A")).thenReturn(Optional.of(local));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(overrides.save(any(InheritanceOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InheritanceOverride saved = service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inherited.versionId(),
            local.versionId(),
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.REPLACE,
            "给药剂量阈值更严格",
            "本院药事会评审前置",
            "HOSP-A 成人住院",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.INHERITABLE
        ));

        assertThat(saved.lifecycleStatus()).isEqualTo(InheritanceOverrideStatus.IN_REVIEW);
        assertThat(saved.overrideReason()).contains("本院药事会");
    }

    @Test
    void canRegisterTenantOverrideAgainstPlatformBaselineWithoutCopyingPlatformVersion() {
        OrgUnit hospital = org("hospital-a", null, "/TENANT-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        AssetVersion platformBaseline = version(
            "av-platform-v1",
            PlatformTenant.ID,
            "1.0.0",
            PlatformAuthority.PLATFORM_ORG_PATH,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE);
        AssetVersion local = version("av-hospital-v1p", "1.0.0-hosp-a", hospital.orgPath(), AssetVersionSafetyPolicy.NORMAL);

        when(assetVersions.findByVersionIdAndTenantId(platformBaseline.versionId(), "tenant-A"))
            .thenReturn(Optional.empty());
        when(assetVersions.findByVersionIdAndTenantId(platformBaseline.versionId(), PlatformTenant.ID))
            .thenReturn(Optional.of(platformBaseline));
        when(assetVersions.findByVersionIdAndTenantId(local.versionId(), "tenant-A")).thenReturn(Optional.of(local));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(hospital));
        when(overrides.save(any(InheritanceOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InheritanceOverride saved = service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            platformBaseline.versionId(),
            local.versionId(),
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.REPLACE,
            "平台规则首发后本院增加本地解释",
            "首发开通初始覆盖",
            "HOSP-A 成人住院",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.INHERITABLE
        ));

        assertThat(saved.inheritedVersionId()).isEqualTo(platformBaseline.versionId());
        assertThat(saved.tenantId()).isEqualTo("tenant-A");
        assertThat(saved.lifecycleStatus()).isEqualTo(InheritanceOverrideStatus.PUBLISHED);
    }

    @Test
    void deniesLowerOrgDisablingInheritedSafetyRedline() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.REGION, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        AssetVersion redline = version("av-redline-v1", "1.0.0", group.orgPath(), AssetVersionSafetyPolicy.SAFETY_REDLINE);

        when(assetVersions.findByVersionIdAndTenantId(redline.versionId(), "tenant-A")).thenReturn(Optional.of(redline));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));

        assertThatThrownBy(() -> service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            redline.versionId(),
            null,
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.DISABLE,
            "本院暂不启用集团高风险禁忌红线",
            "等待本院确认",
            "HOSP-A",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.INHERITABLE
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("INHERITANCE_SAFETY_DENIED")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INHERITANCE_SAFETY_DENIED);

        verify(overrides, never()).save(any(InheritanceOverride.class));
    }

    @Test
    void registersDisableOverrideWithEvidenceForFreeBaseline() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.REGION, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        AssetVersion inherited = version("av-group-v1", "1.0.0", group.orgPath(), AssetVersionSafetyPolicy.NORMAL);

        when(assetVersions.findByVersionIdAndTenantId(inherited.versionId(), "tenant-A")).thenReturn(Optional.of(inherited));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));
        when(overrides.save(any(InheritanceOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InheritanceOverride saved = service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inherited.versionId(),
            null,
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.DISABLE,
            "本院暂停继承集团规则",
            "本院流程暂不适用该集团规则",
            "HOSP-A 成人住院",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.INHERITABLE
        ));

        // 停用无替换版本，但必须留下原因/影响/操作者/trace 作为发布证据链
        assertThat(saved.overrideMode()).isEqualTo(InheritanceOverrideMode.DISABLE);
        assertThat(saved.overrideVersionId()).isNull();
        assertThat(saved.orgPath()).isEqualTo(hospital.orgPath());
        assertThat(saved.overrideReason()).isEqualTo("本院流程暂不适用该集团规则");
        assertThat(saved.impactScope()).isEqualTo("HOSP-A 成人住院");
        assertThat(saved.propagation()).isEqualTo(InheritancePropagation.INHERITABLE);
        assertThat(saved.createdBy()).isEqualTo("publisher-1");
        assertThat(saved.createdAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void deniesDisableOfLockedBaseline() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.REGION, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.FACILITY, "HOSP-A");
        AssetVersion locked = version(
            "av-locked-v1", "1.0.0", group.orgPath(),
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.LOCKED);

        when(assetVersions.findByVersionIdAndTenantId(locked.versionId(), "tenant-A")).thenReturn(Optional.of(locked));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));

        assertThatThrownBy(() -> service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            locked.versionId(),
            null,
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.DISABLE,
            "本院想关闭锁定基线",
            "本院流程",
            "HOSP-A",
            "publisher-1",
            "trace-sys04",
            InheritancePropagation.INHERITABLE
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("INHERITANCE_SAFETY_DENIED")
            .hasMessageContaining("锁定基线")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INHERITANCE_SAFETY_DENIED);

        verify(overrides, never()).save(any(InheritanceOverride.class));
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
            level == OrgLevel.FACILITY ? OrgFacilityType.HOSPITAL : null,
            null,
            OrgUnitStatus.ACTIVE,
            CLOCK.instant(),
            "admin-1",
            CLOCK.instant(),
            "admin-1"
        );
    }

    private AssetVersion version(String versionId, String versionNo, String orgPath, AssetVersionSafetyPolicy safetyPolicy) {
        return version(versionId, versionNo, orgPath, safetyPolicy, AssetVersionOverridePolicy.FREE);
    }

    private AssetVersion version(
            String versionId,
            String versionNo,
            String orgPath,
            AssetVersionSafetyPolicy safetyPolicy,
            AssetVersionOverridePolicy overridePolicy) {
        Instant now = CLOCK.instant();
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

    private AssetVersion version(
            String versionId,
            String tenantId,
            String versionNo,
            String orgPath,
            AssetVersionSafetyPolicy safetyPolicy,
            AssetVersionOverridePolicy overridePolicy) {
        Instant now = CLOCK.instant();
        return new AssetVersion(
            1L,
            versionId,
            tenantId,
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            orgPath,
            "adult|inpatient",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
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
}
