package com.medkernel.engine.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.PlatformTenant;

class ReleaseCandidateQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-23T10:00:00Z");
    private final AssetVersionRepository versions = mock(AssetVersionRepository.class);
    private final OrgUnitRepository organizations = mock(OrgUnitRepository.class);
    private final OrgHierarchyRepository hierarchy = mock(OrgHierarchyRepository.class);
    private ReleaseCandidateQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReleaseCandidateQueryService(versions, organizations, hierarchy);
    }

    @Test
    void listsPlatformDraftRuntimeAssetsWithoutAnyReleaseContainer() {
        when(versions.pagePlatformReleaseCandidates(
                PlatformTenant.ID, null, null, 0, 20))
            .thenReturn(List.of(
                version(PlatformTenant.ID, VersionedAssetType.RULE, "RULE.CKD", "rule-v2",
                    "V2", AssetVersionStatus.DRAFT, "/platform")
            ));
        when(versions.countPlatformReleaseCandidates(
                PlatformTenant.ID, null, null))
            .thenReturn(1L);

        var result = service.platformCandidates(
            null, null, new PageRequest(1, 20, null));

        assertThat(result.items())
            .extracting(ReleaseCandidateAsset::assetType)
            .containsExactly(VersionedAssetType.RULE);
        assertThat(result.items().getFirst().sourceLayer())
            .isEqualTo(ReleaseSourceLayer.PLATFORM);
        assertThat(result.items().getFirst().versionId()).isEqualTo("rule-v2");
    }

    @Test
    void listsOnlyHospitalApplicableLocalDraftOrPublishedAssets() {
        OrgUnit tenant = org("tenant-root", null, "/tenant-a", OrgLevel.TENANT, null);
        OrgUnit region = org("region-a", "tenant-root", "/tenant-a/region-a", OrgLevel.REGION, null);
        OrgUnit hospital = org(
            "hospital-a", "region-a", "/tenant-a/region-a/hospital-a",
            OrgLevel.FACILITY, OrgFacilityType.HOSPITAL);
        when(organizations.findByTenantIdAndId("tenant-a", "hospital-a"))
            .thenReturn(java.util.Optional.of(hospital));
        when(hierarchy.findAncestorsAndSelf("tenant-a", "hospital-a"))
            .thenReturn(List.of(tenant, region, hospital));
        when(versions.pageHospitalReleaseCandidates(
                eq("tenant-a"),
                eq(List.of("/tenant-a", "/tenant-a/region-a", "/tenant-a/region-a/hospital-a")),
                eq(VersionedAssetType.PATHWAY),
                eq("肾病"),
                anyInt(),
                anyInt()))
            .thenReturn(List.of(
                version("tenant-a", VersionedAssetType.PATHWAY, "PATH.CKD", "path-v3",
                    "V3", AssetVersionStatus.PUBLISHED, hospital.orgPath())
            ));
        when(versions.countHospitalReleaseCandidates(
                eq("tenant-a"),
                any(),
                eq(VersionedAssetType.PATHWAY),
                eq("肾病")))
            .thenReturn(1L);

        var result = service.hospitalCandidates(
            "tenant-a",
            "hospital-a",
            VersionedAssetType.PATHWAY,
            "肾病",
            new PageRequest(1, 20, null));

        assertThat(result.items()).singleElement().satisfies(candidate -> {
            assertThat(candidate.sourceLayer()).isEqualTo(ReleaseSourceLayer.HOSPITAL);
            assertThat(candidate.assetIdentity()).isEqualTo("PATH.CKD");
            assertThat(candidate.versionNo()).isEqualTo("V3");
        });
    }

    private AssetVersion version(
            String tenantId,
            VersionedAssetType type,
            String identity,
            String versionId,
            String versionNo,
            AssetVersionStatus status,
            String orgPath) {
        return new AssetVersion(
            1L, versionId, tenantId, type, identity, versionNo,
            orgPath, "ALL", "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            status, "version:" + versionId, "source",
            null, null, NOW, "operator", NOW, "operator", "trace");
    }

    private OrgUnit org(
            String id,
            String parentId,
            String path,
            OrgLevel level,
            OrgFacilityType facilityType) {
        return new OrgUnit(
            id, parentId, "tenant-a", path, level, id, id, null,
            facilityType, null, OrgUnitStatus.ACTIVE,
            NOW, "operator", NOW, "operator");
    }
}
