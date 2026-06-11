package com.medkernel.engine.org;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.shared.context.OrgLevel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrgUnitRepository 集成测试：H2 + Flyway V1+V2 + Spring Data JDBC。
 *
 * <p>{@link DataJdbcTest} 默认禁用 Flyway；通过 {@link ImportAutoConfiguration} 把它重新挂回来，
 * 让 V1__init.sql + V2__org_audit_baseline.sql 创建出 org_unit 表后再跑测试。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(OrgUnitIdGenerator.class)
@TestPropertySource(properties = {
    // DATABASE_TO_LOWER 让 H2 把所有标识符存为小写，匹配 Spring Data JDBC 默认带引号的 SQL（"org_unit"）
    "spring.datasource.url=jdbc:h2:mem:orgunit-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class OrgUnitRepositoryTest {

    @Autowired
    OrgUnitRepository repository;

    @Autowired
    JdbcClient jdbc;

    private OrgUnit hospital;
    private OrgUnit department;

    @BeforeEach
    void setUpHierarchy() {
        hospital = repository.save(newHospital("t-scope", "HOSP-SCOPE", "范围医院"));
        department = repository.save(new OrgUnit(
            null, hospital.id(), "t-scope", hospital.orgPath() + "/DEPT-SCOPE",
            OrgLevel.DEPARTMENT, "DEPT-SCOPE", "范围科室",
            null, null, null, OrgUnitStatus.ACTIVE,
            Instant.now(), "system", Instant.now(), "system"
        ));
        jdbc.sql("""
            INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth)
            VALUES (:tenantId, :ancestorId, :descendantId, :depth)
            """)
            .param("tenantId", "t-scope")
            .param("ancestorId", hospital.id())
            .param("descendantId", hospital.id())
            .param("depth", 0)
            .update();
        jdbc.sql("""
            INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth)
            VALUES (:tenantId, :ancestorId, :descendantId, :depth)
            """)
            .param("tenantId", "t-scope")
            .param("ancestorId", hospital.id())
            .param("descendantId", department.id())
            .param("depth", 1)
            .update();
    }

    @AfterEach
    void wipe() {
        jdbc.sql("DELETE FROM org_closure").update();
        repository.deleteAll();
    }

    @Test
    void persistsAndReadsBackOrgUnit() {
        OrgUnit hospital = newHospital("t-1", "HOSP-001", "总院");
        OrgUnit saved = repository.save(hospital);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.tenantId()).isEqualTo("t-1");
        assertThat(saved.level()).isEqualTo(OrgLevel.FACILITY);
        assertThat(saved.status()).isEqualTo(OrgUnitStatus.ACTIVE);

        Optional<OrgUnit> reloaded = repository.findByTenantIdAndCode("t-1", "HOSP-001");
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().name()).isEqualTo("总院");
    }

    @Test
    void findsByLevel() {
        repository.save(newHospital("t-1", "HOSP-001", "总院"));
        repository.save(newHospital("t-1", "HOSP-002", "东区分院"));
        repository.save(newDepartment("t-1", "DEPT-CARDIO", "心内科"));

        List<OrgUnit> hospitals = repository.findByTenantIdAndLevelOrderByCodeAsc("t-1", OrgLevel.FACILITY);
        assertThat(hospitals).hasSize(2);
        assertThat(hospitals).extracting(OrgUnit::code).containsExactly("HOSP-001", "HOSP-002");

        List<OrgUnit> departments = repository.findByTenantIdAndLevelOrderByCodeAsc("t-1", OrgLevel.DEPARTMENT);
        assertThat(departments).hasSize(1);
    }

    @Test
    void isolatesByTenant() {
        repository.save(newHospital("t-1", "HOSP-001", "A 院"));
        repository.save(newHospital("t-2", "HOSP-001", "B 院"));

        assertThat(repository.countByTenantId("t-1")).isEqualTo(1);
        assertThat(repository.countByTenantId("t-2")).isEqualTo(1);

        Optional<OrgUnit> aSide = repository.findByTenantIdAndCode("t-1", "HOSP-001");
        Optional<OrgUnit> bSide = repository.findByTenantIdAndCode("t-2", "HOSP-001");
        assertThat(aSide).isPresent();
        assertThat(bSide).isPresent();
        assertThat(aSide.get().name()).isEqualTo("A 院");
        assertThat(bSide.get().name()).isEqualTo("B 院");
    }

    @Test
    void pageReturnsRequestedRange() {
        for (int i = 1; i <= 25; i++) {
            String code = String.format("HOSP-%03d", i);
            repository.save(newHospital("t-page", code, "院 " + i));
        }
        List<OrgUnit> firstPage = repository.pageByTenantId("t-page", 0, 10);
        List<OrgUnit> thirdPage = repository.pageByTenantId("t-page", 20, 10);

        assertThat(firstPage).hasSize(10);
        assertThat(thirdPage).hasSize(5);
        assertThat(firstPage.get(0).code()).isEqualTo("HOSP-001");
        assertThat(thirdPage.get(thirdPage.size() - 1).code()).isEqualTo("HOSP-025");
    }

    @Test
    void directorySearchFiltersKeywordLevelStatusAndTenant() {
        repository.save(newHospital("t-1", "HOSP-MAIN", "中心医院"));
        repository.save(newDepartment("t-1", "DEPT-CARDIO", "心内科"));
        repository.save(new OrgUnit(
            null, null, "t-1", "/DEPT-OLD", OrgLevel.DEPARTMENT, "DEPT-OLD", "旧科室",
            null, null, "cardiology", OrgUnitStatus.SUSPENDED, Instant.now(), "system", Instant.now(), "system"
        ));
        repository.save(newDepartment("t-2", "DEPT-CARDIO", "另一租户心内科"));

        assertThat(repository.countDirectory(
            "t-1", "心内", "DEPARTMENT", "ACTIVE", null, null))
            .isEqualTo(1);
        assertThat(repository.pageDirectory(
            "t-1", "心内", "DEPARTMENT", "ACTIVE", null, null, 0, 10))
            .extracting(OrgUnit::code)
            .containsExactly("DEPT-CARDIO");
        assertThat(repository.pageDirectory(
            "t-1", "cardiology", "DEPARTMENT", "SUSPENDED", null, null, 0, 10))
            .extracting(OrgUnit::code)
            .containsExactly("DEPT-OLD");
        assertThat(repository.pageDirectory(
            "t-1", null, null, null, null, null, 1, 1))
            .hasSize(1);
    }

    @Test
    void directorySearchSupportsBusinessScopeAndAncestor() {
        assertThat(repository.countDirectory(
            "t-scope", null, null, "ACTIVE", OrgDirectoryScope.SERVICE_ORGANIZATION.name(), null))
            .isEqualTo(1);
        assertThat(repository.pageDirectory(
            "t-scope", null, "DEPARTMENT", "ACTIVE", null, hospital.id(), 0, 10))
            .extracting(OrgUnit::id)
            .containsExactly(department.id());
    }

    private OrgUnit newHospital(String tenantId, String code, String name) {
        Instant now = Instant.now();
        return new OrgUnit(null, null, tenantId, "/" + code, OrgLevel.FACILITY, code, name, null, OrgFacilityType.HOSPITAL, null,
            OrgUnitStatus.ACTIVE, now, "system", now, "system");
    }

    private OrgUnit newDepartment(String tenantId, String code, String name) {
        Instant now = Instant.now();
        return new OrgUnit(null, null, tenantId, "/" + code, OrgLevel.DEPARTMENT, code, name, null, null, null,
            OrgUnitStatus.ACTIVE, now, "system", now, "system");
    }
}
