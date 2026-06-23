package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;

class AssetScopeResolverTest {

    private static final Instant NOW = Instant.parse("2026-06-23T12:00:00Z");
    private final OrgUnitRepository organizations = mock(OrgUnitRepository.class);
    private final OrgHierarchyRepository hierarchy = mock(OrgHierarchyRepository.class);
    private AssetScopeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AssetScopeResolver(organizations, hierarchy);
    }

    @Test
    void resolvesPlatformToTheUniqueAuthorityScopeWithoutReadingTenantOrganizations() {
        AssetOwnershipScope result = resolver.resolve(
            PlatformTenant.ID,
            OrgScope.tenant(PlatformTenant.ID));

        assertThat(result.sourceLayer()).isEqualTo(ReleaseSourceLayer.PLATFORM);
        assertThat(result.organizationPath()).isEqualTo(PlatformAuthority.PLATFORM_ORG_PATH);
        verify(organizations, never()).findByTenantIdAndParentIdIsNull(PlatformTenant.ID);
    }

    @Test
    void resolvesTenantOrRegionContextToItsRealGroupPath() {
        OrgUnit region = unit(
            "group-A", "root-A", "/tenant-A/group-A", OrgLevel.REGION, null,
            OrgUnitStatus.ACTIVE);
        when(hierarchy.findAncestorsAndSelf("tenant-A", "group-A"))
            .thenReturn(List.of(
                unit("root-A", null, "/tenant-A", OrgLevel.TENANT, null,
                    OrgUnitStatus.ACTIVE),
                region));

        AssetOwnershipScope result = resolver.resolve(
            "tenant-A",
            new OrgScope("tenant-A", "group-A", null, null, null, null, null, null));

        assertThat(result.sourceLayer()).isEqualTo(ReleaseSourceLayer.GROUP);
        assertThat(result.organizationPath()).isEqualTo("/tenant-A/group-A");
    }

    @Test
    void resolvesDepartmentContextToItsOwningFacilityInsteadOfCreatingDepartmentReleaseLayers() {
        OrgUnit hospital = unit(
            "hospital-A", "group-A", "/tenant-A/group-A/hospital-A",
            OrgLevel.FACILITY, OrgFacilityType.HOSPITAL, OrgUnitStatus.ACTIVE);
        when(hierarchy.findAncestorsAndSelf("tenant-A", "department-A"))
            .thenReturn(List.of(
                unit("root-A", null, "/tenant-A", OrgLevel.TENANT, null,
                    OrgUnitStatus.ACTIVE),
                unit("group-A", "root-A", "/tenant-A/group-A", OrgLevel.REGION, null,
                    OrgUnitStatus.ACTIVE),
                hospital,
                unit(
                    "department-A", "hospital-A",
                    "/tenant-A/group-A/hospital-A/department-A",
                    OrgLevel.DEPARTMENT, null, OrgUnitStatus.ACTIVE)));

        AssetOwnershipScope result = resolver.resolve(
            "tenant-A",
            new OrgScope(
                "tenant-A", "group-A", "hospital-A", null, null,
                "department-A", null, null));

        assertThat(result.sourceLayer()).isEqualTo(ReleaseSourceLayer.HOSPITAL);
        assertThat(result.organizationPath())
            .isEqualTo("/tenant-A/group-A/hospital-A");
    }

    @Test
    void resolvesAndValidatesPersistedOrganizationOwnerPaths() {
        OrgUnit hospital = unit(
            "hospital-A", "group-A", "/tenant-A/group-A/hospital-A",
            OrgLevel.FACILITY, OrgFacilityType.HOSPITAL, OrgUnitStatus.ACTIVE);
        when(organizations.findByTenantIdAndOrgPath(
            "tenant-A", "/tenant-A/group-A/hospital-A"))
            .thenReturn(Optional.of(hospital));

        AssetOwnershipScope result = resolver.resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-A");

        assertThat(result.sourceLayer()).isEqualTo(ReleaseSourceLayer.HOSPITAL);
        assertThat(result.organizationPath()).isEqualTo(hospital.orgPath());
    }

    @Test
    void canonicalizesAnExplicitDepartmentPathToItsOwningFacility() {
        OrgUnit hospital = unit(
            "hospital-A", "group-A", "/tenant-A/group-A/hospital-A",
            OrgLevel.FACILITY, OrgFacilityType.HOSPITAL, OrgUnitStatus.ACTIVE);
        OrgUnit department = unit(
            "department-A", "hospital-A",
            "/tenant-A/group-A/hospital-A/department-A",
            OrgLevel.DEPARTMENT, null, OrgUnitStatus.ACTIVE);
        when(organizations.findByTenantIdAndOrgPath(
            "tenant-A", department.orgPath()))
            .thenReturn(Optional.of(department));
        when(hierarchy.findAncestorsAndSelf("tenant-A", "department-A"))
            .thenReturn(List.of(
                unit("root-A", null, "/tenant-A", OrgLevel.TENANT, null,
                    OrgUnitStatus.ACTIVE),
                unit("group-A", "root-A", "/tenant-A/group-A", OrgLevel.REGION, null,
                    OrgUnitStatus.ACTIVE),
                hospital,
                department));

        AssetOwnershipScope result = resolver.resolveOrganizationPath(
            "tenant-A", department.orgPath());

        assertThat(result.sourceLayer()).isEqualTo(ReleaseSourceLayer.HOSPITAL);
        assertThat(result.organizationPath()).isEqualTo(hospital.orgPath());
    }

    @Test
    void rejectsCrossTenantOrInactiveOrganizationContext() {
        assertThatThrownBy(() -> resolver.resolve(
            "tenant-A",
            OrgScope.tenant("tenant-B")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("租户");

        when(hierarchy.findAncestorsAndSelf("tenant-A", "hospital-A"))
            .thenReturn(List.of(unit(
                "hospital-A", "group-A", "/tenant-A/group-A/hospital-A",
                OrgLevel.FACILITY, OrgFacilityType.HOSPITAL,
                OrgUnitStatus.SUSPENDED)));

        assertThatThrownBy(() -> resolver.resolve(
            "tenant-A",
            new OrgScope(
                "tenant-A", "group-A", "hospital-A", null, null,
                null, null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("停用或归档");
    }

    private OrgUnit unit(
            String id,
            String parentId,
            String orgPath,
            OrgLevel level,
            OrgFacilityType facilityType,
            OrgUnitStatus status) {
        return new OrgUnit(
            id,
            parentId,
            "tenant-A",
            orgPath,
            level,
            id,
            id,
            null,
            facilityType,
            null,
            status,
            NOW,
            "system",
            NOW,
            "system"
        );
    }
}
