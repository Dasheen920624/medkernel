package com.medkernel.engine.org;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class OrgAssignmentValidatorTest {

    private OrgUnitRepository orgUnits;
    private TenantUserRepository users;
    private OrgAssignmentValidator validator;

    @BeforeEach
    void setUp() {
        orgUnits = Mockito.mock(OrgUnitRepository.class);
        users = Mockito.mock(TenantUserRepository.class);
        validator = new OrgAssignmentValidator(orgUnits, users);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-assignment",
            OrgScope.tenant("tenant-A"),
            "qa-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void acceptsActiveDepartmentAndActiveUserFromCurrentTenant() {
        Mockito.when(orgUnits.findByTenantIdAndId("tenant-A", "dept-1"))
            .thenReturn(Optional.of(orgUnit("dept-1", OrgLevel.DEPARTMENT, OrgUnitStatus.ACTIVE)));
        Mockito.when(users.findByTenantIdAndUserId("tenant-A", "user-1"))
            .thenReturn(Optional.of(user("user-1", "ACTIVE")));

        validator.requireActiveDepartment("dept-1");
        validator.requireActiveUserIfPresent("user-1");
    }

    @Test
    void rejectsNonDepartmentOrSuspendedOrganization() {
        Mockito.when(orgUnits.findByTenantIdAndId("tenant-A", "hospital-1"))
            .thenReturn(Optional.of(orgUnit("hospital-1", OrgLevel.FACILITY, OrgUnitStatus.ACTIVE)));
        Mockito.when(orgUnits.findByTenantIdAndId("tenant-A", "dept-2"))
            .thenReturn(Optional.of(orgUnit("dept-2", OrgLevel.DEPARTMENT, OrgUnitStatus.SUSPENDED)));

        assertThatThrownBy(() -> validator.requireActiveDepartment("hospital-1"))
            .isInstanceOf(ApiException.class)
            .hasMessage("责任组织必须是科室");
        assertThatThrownBy(() -> validator.requireActiveDepartment("dept-2"))
            .isInstanceOf(ApiException.class)
            .hasMessage("责任科室未启用");
    }

    @Test
    void rejectsMissingOrDisabledUser() {
        Mockito.when(users.findByTenantIdAndUserId("tenant-A", "disabled-1"))
            .thenReturn(Optional.of(user("disabled-1", "DISABLED")));

        assertThatThrownBy(() -> validator.requireActiveUserIfPresent("missing-1"))
            .isInstanceOf(ApiException.class)
            .hasMessage("责任人不存在");
        assertThatThrownBy(() -> validator.requireActiveUserIfPresent("disabled-1"))
            .isInstanceOf(ApiException.class)
            .hasMessage("责任人未启用");
    }

    @Test
    void acceptsCoherentActiveScopeHierarchyAndSpecialty() {
        OrgUnit group = orgUnit("group-1", null, OrgLevel.REGION, OrgUnitStatus.ACTIVE, null);
        OrgUnit hospital = orgUnit("hospital-1", "group-1", OrgLevel.FACILITY, OrgUnitStatus.ACTIVE, null);
        OrgUnit department = orgUnit(
            "dept-1",
            "hospital-1",
            OrgLevel.DEPARTMENT,
            OrgUnitStatus.ACTIVE,
            "CARDIOLOGY"
        );
        when(orgUnits.findByTenantIdAndId("tenant-A", "group-1")).thenReturn(Optional.of(group));
        when(orgUnits.findByTenantIdAndId("tenant-A", "hospital-1")).thenReturn(Optional.of(hospital));
        when(orgUnits.findByTenantIdAndId("tenant-A", "dept-1")).thenReturn(Optional.of(department));
        when(orgUnits.findByTenantIdAndSpecialtyIdOrderByCodeAsc("tenant-A", "CARDIOLOGY"))
            .thenReturn(List.of(department));

        validator.requireActiveScopeReferences(
            "tenant-A", "group-1", "hospital-1", null, null, "dept-1", "CARDIOLOGY");
    }

    @Test
    void rejectsWrongLevelSuspendedOrCrossBranchScopeReferences() {
        OrgUnit group = orgUnit("group-1", null, OrgLevel.REGION, OrgUnitStatus.ACTIVE, null);
        OrgUnit wrongHospital = orgUnit("hospital-1", null, OrgLevel.REGION, OrgUnitStatus.ACTIVE, null);
        OrgUnit otherHospital = orgUnit("hospital-2", null, OrgLevel.FACILITY, OrgUnitStatus.ACTIVE, null);
        OrgUnit department = orgUnit(
            "dept-1",
            "hospital-2",
            OrgLevel.DEPARTMENT,
            OrgUnitStatus.ACTIVE,
            null
        );
        when(orgUnits.findByTenantIdAndId("tenant-A", "group-1")).thenReturn(Optional.of(group));
        when(orgUnits.findByTenantIdAndId("tenant-A", "hospital-1")).thenReturn(Optional.of(wrongHospital));
        when(orgUnits.findByTenantIdAndId("tenant-A", "hospital-2")).thenReturn(Optional.of(otherHospital));
        when(orgUnits.findByTenantIdAndId("tenant-A", "dept-1")).thenReturn(Optional.of(department));

        assertThatThrownBy(() -> validator.requireActiveScopeReferences(
            "tenant-A", "group-1", "hospital-1", null, null, null, null))
            .isInstanceOf(ApiException.class)
            .hasMessage("机构组织层级不正确");

        assertThatThrownBy(() -> validator.requireActiveScopeReferences(
            "tenant-A", "group-1", "hospital-2", null, null, "dept-1", null))
            .isInstanceOf(ApiException.class)
            .hasMessage("机构不属于已选区域");
    }

    @Test
    void rejectsUnknownOrInactiveSpecialty() {
        OrgUnit department = orgUnit(
            "dept-1",
            null,
            OrgLevel.DEPARTMENT,
            OrgUnitStatus.SUSPENDED,
            "CARDIOLOGY"
        );
        when(orgUnits.findByTenantIdAndSpecialtyIdOrderByCodeAsc("tenant-A", "CARDIOLOGY"))
            .thenReturn(List.of(department));

        assertThatThrownBy(() -> validator.requireActiveScopeReferences(
            "tenant-A", null, null, null, null, null, "CARDIOLOGY"))
            .isInstanceOf(ApiException.class)
            .hasMessage("专科不存在或未启用");
    }

    private OrgUnit orgUnit(String id, OrgLevel level, OrgUnitStatus status) {
        return orgUnit(id, null, level, status, null);
    }

    private OrgUnit orgUnit(
            String id,
            String parentId,
            OrgLevel level,
            OrgUnitStatus status,
            String specialtyId) {
        return new OrgUnit(
            id,
            parentId,
            "tenant-A",
            "/" + id,
            level,
            id,
            id,
            null,
            facilityType(level),
            specialtyId,
            status,
            Instant.now(),
            "system",
            Instant.now(),
            "system");
    }

    private OrgFacilityType facilityType(OrgLevel level) {
        return level == OrgLevel.FACILITY ? OrgFacilityType.HOSPITAL : null;
    }

    private TenantUser user(String userId, String status) {
        Instant now = Instant.now();
        return new TenantUser(
            1L,
            "tenant-A",
            userId,
            userId,
            status,
            1L,
            now,
            "system",
            now,
            "system",
            "trace");
    }
}
