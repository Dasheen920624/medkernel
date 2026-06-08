package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceExplanation;
import com.medkernel.engine.versioning.InheritanceResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.PlatformTenant;

class PackageInheritanceImpactServiceTest {

    private static final String TENANT_ID = "tenant-A";
    private static final String ASSET_IDENTITY = "RULE.VTE.RISK";
    private static final String APPLICABLE_SCOPE = "adult|inpatient";
    private static final String HOSPITAL_PATH = "/TENANT-A/FACILITY-HOSP";
    private static final String DEPARTMENT_PATH = "/TENANT-A/FACILITY-HOSP/DEPT-CARDIO";

    private AssetVersionRepository assetVersions;
    private OrgUnitRepository orgUnits;
    private InheritanceResolver inheritanceResolver;
    private PackageInheritanceImpactService service;

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        orgUnits = mock(OrgUnitRepository.class);
        inheritanceResolver = mock(InheritanceResolver.class);
        service = new PackageInheritanceImpactService(assetVersions, orgUnits, inheritanceResolver);
    }

    @Test
    void analyzesPlatformUpstreamChangeWithAutomaticInheritanceAndRebasePrompts() {
        AssetVersion platformV1 = platformVersion("av-platform-v1", "1.0.0", "hash-platform-v1", AssetVersionStatus.PUBLISHED);
        AssetVersion platformV2 = platformVersion("av-platform-v2", "2.0.0", "hash-platform-v2", AssetVersionStatus.PUBLISHED);
        AssetVersion tenantOverride = tenantVersion("av-tenant-local", "1.1.0-hosp", HOSPITAL_PATH, "hash-tenant-local");
        OrgUnit hospital = org("org-hosp", null, HOSPITAL_PATH, OrgLevel.FACILITY);
        OrgUnit department = org("org-cardio", "org-hosp", DEPARTMENT_PATH, OrgLevel.DEPARTMENT);
        OrgUnit ward = org("org-ward", "org-cardio", DEPARTMENT_PATH + "/WARD-A", OrgLevel.WARD);

        when(assetVersions.findByVersionIdAndTenantId("av-platform-v2", PlatformTenant.ID))
            .thenReturn(Optional.of(platformV2));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            PlatformTenant.ID,
            VersionedAssetType.RULE,
            ASSET_IDENTITY + "|" + PlatformAuthority.PLATFORM_ORG_PATH + "|" + APPLICABLE_SCOPE,
            AssetVersionStatus.PUBLISHED
        )).thenReturn(List.of(platformV1));
        when(orgUnits.findByTenantIdOrderByLevelAscCodeAsc(TENANT_ID))
            .thenReturn(List.of(hospital, department, ward));

        when(inheritanceResolver.resolve(query(hospital.id())))
            .thenReturn(new ResolvedAssetVersion(
                platformV1,
                PlatformAuthority.PLATFORM_ORG_PATH,
                true,
                false,
                false,
                new InheritanceExplanation(
                    "继承平台权威基线版本 1.0.0",
                    List.of(PlatformAuthority.PLATFORM_ORG_PATH, HOSPITAL_PATH),
                    null,
                    null,
                    null),
                SourceTier.PLATFORM
            ));
        when(inheritanceResolver.resolve(query(department.id())))
            .thenReturn(new ResolvedAssetVersion(
                tenantOverride,
                HOSPITAL_PATH,
                true,
                true,
                false,
                new InheritanceExplanation(
                    "命中本级局部覆盖版本 1.1.0-hosp",
                    List.of(HOSPITAL_PATH, DEPARTMENT_PATH),
                    "本院 D-二聚体阈值按检验参考区间上调",
                    "本院检验设备参考区间不同",
                    "心内科与急诊"),
                SourceTier.ORG
            ));
        when(inheritanceResolver.resolve(query(ward.id())))
            .thenReturn(new ResolvedAssetVersion(
                null,
                DEPARTMENT_PATH + "/WARD-A",
                false,
                true,
                true,
                new InheritanceExplanation(
                    "本病区停用继承规则",
                    List.of(HOSPITAL_PATH, DEPARTMENT_PATH, DEPARTMENT_PATH + "/WARD-A"),
                    "病区暂不启用该规则",
                    "本病区工作流未接入",
                    "心内科一病区"),
                SourceTier.ORG
            ));

        PackageInheritanceImpactResponse response = service.analyze(
            TENANT_ID,
            VersionedAssetType.RULE,
            ASSET_IDENTITY,
            APPLICABLE_SCOPE,
            "av-platform-v2"
        );

        assertThat(response.upstreamBaseVersion()).isEqualTo("1.0.0");
        assertThat(response.upstreamTargetVersion()).isEqualTo("2.0.0");
        assertThat(response.autoInheritedCount()).isEqualTo(1);
        assertThat(response.rebaseRequiredCount()).isEqualTo(2);
        assertThat(response.upstreamDiff().changes())
            .extracting(PackageDiffChange::changeType)
            .containsExactly(PackageDiffChangeType.UPDATED);
        assertThat(response.targets())
            .extracting(PackageInheritanceImpactTarget::impactType)
            .containsExactly(
                PackageInheritanceImpactType.AUTO_INHERITS_UPSTREAM,
                PackageInheritanceImpactType.REBASE_RECOMMENDED,
                PackageInheritanceImpactType.DISABLE_REVIEW_RECOMMENDED
            );
        assertThat(response.targets().get(1).rebasePrompt())
            .contains("上游平台已变更")
            .contains("建议 rebase")
            .contains("本院 D-二聚体阈值");
    }

    @Test
    void rejectsNonPlatformUpstreamVersion() {
        when(assetVersions.findByVersionIdAndTenantId("av-tenant-local", PlatformTenant.ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(
            TENANT_ID,
            VersionedAssetType.RULE,
            ASSET_IDENTITY,
            APPLICABLE_SCOPE,
            "av-tenant-local"
        ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private InheritanceResolveQuery query(String orgUnitId) {
        return new InheritanceResolveQuery(
            TENANT_ID,
            VersionedAssetType.RULE,
            ASSET_IDENTITY,
            APPLICABLE_SCOPE,
            orgUnitId
        );
    }

    private AssetVersion platformVersion(
            String versionId,
            String versionNo,
            String contentHash,
            AssetVersionStatus status) {
        return version(
            versionId,
            PlatformTenant.ID,
            versionNo,
            PlatformAuthority.PLATFORM_ORG_PATH,
            contentHash,
            status
        );
    }

    private AssetVersion tenantVersion(String versionId, String versionNo, String orgPath, String contentHash) {
        return version(versionId, TENANT_ID, versionNo, orgPath, contentHash, AssetVersionStatus.PUBLISHED);
    }

    private AssetVersion version(
            String versionId,
            String tenantId,
            String versionNo,
            String orgPath,
            String contentHash,
            AssetVersionStatus status) {
        Instant now = Instant.parse("2026-06-09T02:40:00Z");
        return new AssetVersion(
            null,
            versionId,
            tenantId,
            VersionedAssetType.RULE,
            ASSET_IDENTITY,
            versionNo,
            orgPath,
            APPLICABLE_SCOPE,
            contentHash,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            ASSET_IDENTITY + "|" + orgPath + "|" + APPLICABLE_SCOPE,
            "rule/" + ASSET_IDENTITY,
            status == AssetVersionStatus.PUBLISHED ? now : null,
            null,
            now,
            "publisher-1",
            now,
            "publisher-1",
            "trace-impact"
        );
    }

    private OrgUnit org(String id, String parentId, String orgPath, OrgLevel level) {
        Instant now = Instant.parse("2026-06-09T02:40:00Z");
        return new OrgUnit(
            id,
            parentId,
            TENANT_ID,
            orgPath,
            level,
            id.toUpperCase(),
            id,
            id,
            level == OrgLevel.FACILITY ? OrgFacilityType.HOSPITAL : null,
            null,
            OrgUnitStatus.ACTIVE,
            now,
            "seed",
            now,
            "seed"
        );
    }
}
