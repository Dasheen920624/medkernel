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
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;

class InheritanceOverrideServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T08:00:00Z"), ZoneOffset.UTC);

    private AssetVersionRepository assetVersions;
    private InheritanceOverrideRepository overrides;
    private OrgHierarchyRepository hierarchy;
    private InheritanceOverrideService service;

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        overrides = mock(InheritanceOverrideRepository.class);
        hierarchy = mock(OrgHierarchyRepository.class);
        service = new InheritanceOverrideService(assetVersions, overrides, hierarchy, CLOCK);
    }

    @Test
    void registersLocalOverrideWithRequiredExplanation() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.HOSPITAL, "HOSP-A");
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
            "trace-sys04"
        ));

        assertThat(saved.overrideId()).matches("io-[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(saved.orgPath()).isEqualTo(hospital.orgPath());
        assertThat(saved.diffSummary()).isEqualTo("本院 D-二聚体阈值按检验参考区间上调");
        assertThat(saved.overrideReason()).isEqualTo("医院检验科 2026 年参考区间已更新");
        assertThat(saved.impactScope()).isEqualTo("仅 HOSP-A 成人住院");
        assertThat(saved.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(saved.propagation()).isEqualTo(InheritancePropagation.INHERITABLE);
    }

    @Test
    void registersExclusivePropagationWhenRequested() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.HOSPITAL, "HOSP-A");
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
    }

    @Test
    void deniesLowerOrgDisablingInheritedSafetyRedline() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.HOSPITAL, "HOSP-A");
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
            "trace-sys04"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("INHERITANCE_SAFETY_DENIED")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INHERITANCE_SAFETY_DENIED);

        verify(overrides, never()).save(any(InheritanceOverride.class));
    }

    @Test
    void rejectsDisableModeUntilReleaseFlowCanRecordEvidenceAndResolverCanConsumeIt() {
        OrgUnit group = org("group-1", null, "/TENANT-A/GROUP-A", OrgLevel.GROUP, "GROUP-A");
        OrgUnit hospital = org("hospital-a", "group-1", "/TENANT-A/GROUP-A/HOSP-A", OrgLevel.HOSPITAL, "HOSP-A");
        AssetVersion inherited = version("av-group-v1", "1.0.0", group.orgPath(), AssetVersionSafetyPolicy.NORMAL);

        when(assetVersions.findByVersionIdAndTenantId(inherited.versionId(), "tenant-A")).thenReturn(Optional.of(inherited));
        when(hierarchy.findAncestorsAndSelf("tenant-A", hospital.id())).thenReturn(List.of(group, hospital));

        assertThatThrownBy(() -> service.registerOverride(new InheritanceOverrideRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            inherited.versionId(),
            null,
            hospital.id(),
            "adult|inpatient",
            InheritanceOverrideMode.DISABLE,
            "本院暂停继承集团规则",
            "等待发布证据链",
            "HOSP-A",
            "publisher-1",
            "trace-sys04"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("关闭继承仍需发布流证据链")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

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
            null,
            OrgUnitStatus.ACTIVE,
            CLOCK.instant(),
            "admin-1",
            CLOCK.instant(),
            "admin-1"
        );
    }

    private AssetVersion version(String versionId, String versionNo, String orgPath, AssetVersionSafetyPolicy safetyPolicy) {
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
