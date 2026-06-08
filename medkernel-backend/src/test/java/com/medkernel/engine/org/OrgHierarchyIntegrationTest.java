package com.medkernel.engine.org;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BASE-01 AC-1：组织树、闭包路径查询与防环约束的真实 H2 迁移集成测试。
 *
 * <p>SPECIALTY 专病作为横切维度由 applicableScope / specialtyId 表达，不再作为新组织树节点写入。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrgUnitService.class, OrgHierarchyRepository.class, OrgUnitIdGenerator.class})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:org-hierarchy-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class OrgHierarchyIntegrationTest {

    @Autowired
    OrgUnitService service;

    @Autowired
    OrgUnitRepository repository;

    @AfterEach
    void clear() {
        repository.deleteAll();
        RequestContext.clear();
    }

    @Test
    void createsOrgTreePathAndClosureQueriesWithoutSpecialtyNode() {
        RequestContext.restore(new RequestContext.Snapshot("trace-org", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit region = service.createOrgUnit(input(tenant.id(), OrgLevel.REGION, "REGION-A", "医共体A"));
        OrgUnit facility = service.createOrgUnit(input(
            region.id(), OrgLevel.FACILITY, "FAC-B", "医院B", OrgFacilityType.HOSPITAL));
        OrgUnit campus = service.createOrgUnit(input(facility.id(), OrgLevel.CAMPUS, "CAMP-B", "院区B"));
        OrgUnit department = service.createOrgUnit(input(campus.id(), OrgLevel.DEPARTMENT, "DEPT-C", "科室C"));
        OrgUnit ward = service.createOrgUnit(input(department.id(), OrgLevel.WARD, "WARD-C", "病区C"));

        assertThat(facility.facilityType()).isEqualTo(OrgFacilityType.HOSPITAL);
        assertThat(ward.orgPath()).isEqualTo("/TENANT-A/REGION-A/FAC-B/CAMP-B/DEPT-C/WARD-C");
        assertThat(service.orgPathByCurrentTenant("WARD-C"))
            .extracting(OrgUnit::code)
            .containsExactly("TENANT-A", "REGION-A", "FAC-B", "CAMP-B", "DEPT-C", "WARD-C");
        assertThat(service.descendantsByCurrentTenant("REGION-A"))
            .extracting(OrgUnit::code)
            .containsExactly("REGION-A", "FAC-B", "CAMP-B", "DEPT-C", "WARD-C");
    }

    @Test
    void rejectsInvertedLevelOrgCreationWithBusinessErrorCode() {
        RequestContext.restore(new RequestContext.Snapshot("trace-inverted-layer", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit region = service.createOrgUnit(input(tenant.id(), OrgLevel.REGION, "REGION-A", "医共体A"));
        OrgUnit facility = service.createOrgUnit(input(
            region.id(), OrgLevel.FACILITY, "FAC-B", "医院B", OrgFacilityType.HOSPITAL));

        // 把区域挂在机构之下——父级层级更低，倒挂，应拒绝
        assertThatThrownBy(() -> service.createOrgUnit(input(facility.id(), OrgLevel.REGION, "REGION-X", "医共体X")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_LEVEL_INVALID);
    }

    @Test
    void allowsSkipLevelOrgCreationAcrossOptionalLayers() {
        RequestContext.restore(new RequestContext.Snapshot("trace-skip-layer", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit region = service.createOrgUnit(input(tenant.id(), OrgLevel.REGION, "REGION-A", "医共体A"));
        OrgUnit facility = service.createOrgUnit(input(
            region.id(), OrgLevel.FACILITY, "FAC-B", "医院B", OrgFacilityType.HOSPITAL));

        // 科室直挂机构（跳过院区）应成功，组织路径不含被跳过的中间层
        OrgUnit department = service.createOrgUnit(input(facility.id(), OrgLevel.DEPARTMENT, "DEPT-C", "科室C"));

        assertThat(department.orgPath()).isEqualTo("/TENANT-A/REGION-A/FAC-B/DEPT-C");
        assertThat(service.orgPathByCurrentTenant("DEPT-C"))
            .extracting(OrgUnit::code)
            .containsExactly("TENANT-A", "REGION-A", "FAC-B", "DEPT-C");
    }

    @Test
    void rejectsSecondTenantRootForSameTenant() {
        RequestContext.restore(new RequestContext.Snapshot("trace-root", OrgScope.tenant("tenant-A"), "admin-1"));

        service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));

        assertThatThrownBy(() -> service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A-2", "租户A重复根")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void rejectsMovingAncestorUnderDescendant() {
        RequestContext.restore(new RequestContext.Snapshot("trace-cycle", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit region = service.createOrgUnit(input(tenant.id(), OrgLevel.REGION, "REGION-A", "医共体A"));
        OrgUnit facility = service.createOrgUnit(input(
            region.id(), OrgLevel.FACILITY, "FAC-B", "医院B", OrgFacilityType.HOSPITAL));
        OrgUnit campus = service.createOrgUnit(input(facility.id(), OrgLevel.CAMPUS, "CAMP-B", "院区B"));
        OrgUnit department = service.createOrgUnit(input(campus.id(), OrgLevel.DEPARTMENT, "DEPT-C", "科室C"));
        OrgUnit ward = service.createOrgUnit(input(department.id(), OrgLevel.WARD, "WARD-C", "病区C"));

        assertThatThrownBy(() -> service.reparentOrgUnit(region.id(), ward.id()))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void movesSubtreeAndRebuildsPathAndClosure() {
        RequestContext.restore(new RequestContext.Snapshot("trace-move", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit regionA = service.createOrgUnit(input(tenant.id(), OrgLevel.REGION, "REGION-A", "医共体A"));
        OrgUnit regionB = service.createOrgUnit(input(tenant.id(), OrgLevel.REGION, "REGION-B", "医共体B"));
        OrgUnit facility = service.createOrgUnit(input(
            regionA.id(), OrgLevel.FACILITY, "FAC-A", "医院A", OrgFacilityType.HOSPITAL));
        OrgUnit campus = service.createOrgUnit(input(facility.id(), OrgLevel.CAMPUS, "CAMP-A", "院区A"));
        OrgUnit department = service.createOrgUnit(input(campus.id(), OrgLevel.DEPARTMENT, "DEPT-C", "科室C"));
        service.createOrgUnit(input(department.id(), OrgLevel.WARD, "WARD-C", "病区C"));

        OrgUnit moved = service.reparentOrgUnit(facility.id(), regionB.id());

        assertThat(moved.parentId()).isEqualTo(regionB.id());
        assertThat(moved.orgPath()).isEqualTo("/TENANT-A/REGION-B/FAC-A");
        assertThat(service.orgPathByCurrentTenant("WARD-C"))
            .extracting(OrgUnit::code)
            .containsExactly("TENANT-A", "REGION-B", "FAC-A", "CAMP-A", "DEPT-C", "WARD-C");
        assertThat(service.descendantsByCurrentTenant("REGION-A"))
            .extracting(OrgUnit::code)
            .containsExactly("REGION-A");
        assertThat(service.descendantsByCurrentTenant("REGION-B"))
            .extracting(OrgUnit::code)
            .containsExactly("REGION-B", "FAC-A", "CAMP-A", "DEPT-C", "WARD-C");
    }

    @Test
    void secondaryMembershipParticipatesInResolutionPathWithoutChangingPrimaryParent() {
        RequestContext.restore(new RequestContext.Snapshot("trace-dag", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit facility = service.createOrgUnit(input(
            tenant.id(), OrgLevel.FACILITY, "FAC-A", "医院A", OrgFacilityType.HOSPITAL));
        OrgUnit deptA = service.createOrgUnit(input(facility.id(), OrgLevel.DEPARTMENT, "DEPT-A", "科室A"));
        OrgUnit deptB = service.createOrgUnit(input(facility.id(), OrgLevel.DEPARTMENT, "DEPT-B", "专科中心B"));
        OrgUnit ward = service.createOrgUnit(input(deptA.id(), OrgLevel.WARD, "WARD-A", "病区A"));

        service.addSecondaryParent(ward.id(), deptB.id(), "SPECIALTY_CENTER", 10);

        assertThat(repository.findByTenantIdAndId("tenant-A", ward.id()).orElseThrow().parentId())
            .isEqualTo(deptA.id());
        assertThat(service.orgPathByCurrentTenant("WARD-A"))
            .extracting(OrgUnit::code)
            .containsExactly("TENANT-A", "FAC-A", "DEPT-A", "WARD-A");
        assertThat(service.resolutionPathByCurrentTenant("WARD-A"))
            .extracting(OrgUnit::code)
            .containsExactly("TENANT-A", "FAC-A", "DEPT-B", "DEPT-A", "WARD-A");
    }

    private OrgUnit input(String parentId, OrgLevel level, String code, String name) {
        return input(parentId, level, code, name, null);
    }

    private OrgUnit input(String parentId, OrgLevel level, String code, String name, OrgFacilityType facilityType) {
        return new OrgUnit(null, parentId, null, null, level, code, name, null, facilityType, null,
            OrgUnitStatus.ACTIVE, null, null, null, null);
    }
}
