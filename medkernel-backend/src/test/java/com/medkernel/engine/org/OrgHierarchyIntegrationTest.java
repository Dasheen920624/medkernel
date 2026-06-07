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
        OrgUnit group = service.createOrgUnit(input(tenant.id(), OrgLevel.GROUP, "GROUP-A", "集团A"));
        OrgUnit hospital = service.createOrgUnit(input(group.id(), OrgLevel.HOSPITAL, "HOSP-B", "医院B"));
        OrgUnit campus = service.createOrgUnit(input(hospital.id(), OrgLevel.CAMPUS, "CAMP-B", "院区B"));
        OrgUnit site = service.createOrgUnit(input(campus.id(), OrgLevel.SITE, "SITE-B", "服务点B"));
        OrgUnit department = service.createOrgUnit(input(site.id(), OrgLevel.DEPARTMENT, "DEPT-C", "科室C"));

        assertThat(department.orgPath()).isEqualTo("/TENANT-A/GROUP-A/HOSP-B/CAMP-B/SITE-B/DEPT-C");
        assertThat(service.orgPathByCurrentTenant("DEPT-C"))
            .extracting(OrgUnit::code)
            .containsExactly("TENANT-A", "GROUP-A", "HOSP-B", "CAMP-B", "SITE-B", "DEPT-C");
        assertThat(service.descendantsByCurrentTenant("GROUP-A"))
            .extracting(OrgUnit::code)
            .containsExactly("GROUP-A", "HOSP-B", "CAMP-B", "SITE-B", "DEPT-C");
    }

    @Test
    void rejectsInvertedLevelOrgCreationWithBusinessErrorCode() {
        RequestContext.restore(new RequestContext.Snapshot("trace-inverted-layer", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit group = service.createOrgUnit(input(tenant.id(), OrgLevel.GROUP, "GROUP-A", "集团A"));
        OrgUnit hospital = service.createOrgUnit(input(group.id(), OrgLevel.HOSPITAL, "HOSP-B", "医院B"));

        // 把集团挂在医院之下——父级层级更低，倒挂，应拒绝
        assertThatThrownBy(() -> service.createOrgUnit(input(hospital.id(), OrgLevel.GROUP, "GROUP-X", "集团X")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_LEVEL_INVALID);
    }

    @Test
    void allowsSkipLevelOrgCreationAcrossOptionalLayers() {
        RequestContext.restore(new RequestContext.Snapshot("trace-skip-layer", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit group = service.createOrgUnit(input(tenant.id(), OrgLevel.GROUP, "GROUP-A", "集团A"));
        OrgUnit hospital = service.createOrgUnit(input(group.id(), OrgLevel.HOSPITAL, "HOSP-B", "医院B"));

        // 科室直挂医院（跳过院区 / 服务点）应成功，组织路径不含被跳过的中间层
        OrgUnit department = service.createOrgUnit(input(hospital.id(), OrgLevel.DEPARTMENT, "DEPT-C", "科室C"));

        assertThat(department.orgPath()).isEqualTo("/TENANT-A/GROUP-A/HOSP-B/DEPT-C");
        assertThat(service.orgPathByCurrentTenant("DEPT-C"))
            .extracting(OrgUnit::code)
            .containsExactly("TENANT-A", "GROUP-A", "HOSP-B", "DEPT-C");
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
        OrgUnit group = service.createOrgUnit(input(tenant.id(), OrgLevel.GROUP, "GROUP-A", "集团A"));
        OrgUnit hospital = service.createOrgUnit(input(group.id(), OrgLevel.HOSPITAL, "HOSP-B", "医院B"));
        OrgUnit campus = service.createOrgUnit(input(hospital.id(), OrgLevel.CAMPUS, "CAMP-B", "院区B"));
        OrgUnit site = service.createOrgUnit(input(campus.id(), OrgLevel.SITE, "SITE-B", "服务点B"));
        OrgUnit department = service.createOrgUnit(input(site.id(), OrgLevel.DEPARTMENT, "DEPT-C", "科室C"));

        assertThatThrownBy(() -> service.reparentOrgUnit(group.id(), department.id()))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void movesSubtreeAndRebuildsPathAndClosure() {
        RequestContext.restore(new RequestContext.Snapshot("trace-move", OrgScope.tenant("tenant-A"), "admin-1"));

        OrgUnit tenant = service.createOrgUnit(input(null, OrgLevel.TENANT, "TENANT-A", "租户A"));
        OrgUnit groupA = service.createOrgUnit(input(tenant.id(), OrgLevel.GROUP, "GROUP-A", "集团A"));
        OrgUnit groupB = service.createOrgUnit(input(tenant.id(), OrgLevel.GROUP, "GROUP-B", "集团B"));
        OrgUnit hospital = service.createOrgUnit(input(groupA.id(), OrgLevel.HOSPITAL, "HOSP-A", "医院A"));
        OrgUnit campus = service.createOrgUnit(input(hospital.id(), OrgLevel.CAMPUS, "CAMP-A", "院区A"));
        OrgUnit site = service.createOrgUnit(input(campus.id(), OrgLevel.SITE, "SITE-A", "服务点A"));
        service.createOrgUnit(input(site.id(), OrgLevel.DEPARTMENT, "DEPT-C", "科室C"));

        OrgUnit moved = service.reparentOrgUnit(hospital.id(), groupB.id());

        assertThat(moved.parentId()).isEqualTo(groupB.id());
        assertThat(moved.orgPath()).isEqualTo("/TENANT-A/GROUP-B/HOSP-A");
        assertThat(service.orgPathByCurrentTenant("DEPT-C"))
            .extracting(OrgUnit::code)
            .containsExactly("TENANT-A", "GROUP-B", "HOSP-A", "CAMP-A", "SITE-A", "DEPT-C");
        assertThat(service.descendantsByCurrentTenant("GROUP-A"))
            .extracting(OrgUnit::code)
            .containsExactly("GROUP-A");
        assertThat(service.descendantsByCurrentTenant("GROUP-B"))
            .extracting(OrgUnit::code)
            .containsExactly("GROUP-B", "HOSP-A", "CAMP-A", "SITE-A", "DEPT-C");
    }

    private OrgUnit input(String parentId, OrgLevel level, String code, String name) {
        return new OrgUnit(null, parentId, null, null, level, code, name, null, null,
            OrgUnitStatus.ACTIVE, null, null, null, null);
    }
}
