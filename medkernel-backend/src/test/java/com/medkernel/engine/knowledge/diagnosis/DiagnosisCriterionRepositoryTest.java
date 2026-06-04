package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/** 诊断标准仓储切片测试：真实 H2 + Flyway 到 V75，验证租户 + 版本作用域读取、id 升序与租户级删除。 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:diagnosis-criterion-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class DiagnosisCriterionRepositoryTest {

    @Autowired
    DiagnosisCriterionRepository repository;

    @AfterEach
    void wipe() {
        repository.deleteAll();
    }

    @Test
    void findsOnlyTenantAndVersionScopedCriteriaOrderedById() {
        repository.save(criterion("t-1", 10L, "FEVER", DiagnosisDirection.REQUIRED));
        repository.save(criterion("t-1", 10L, "COUGH", DiagnosisDirection.SUPPORTING));
        repository.save(criterion("t-1", 20L, "RASH", DiagnosisDirection.SUPPORTING));
        repository.save(criterion("t-2", 10L, "OTHER", DiagnosisDirection.SUPPORTING));

        List<DiagnosisCriterion> result = repository.findByTenantIdAndDiagnosisVersionId("t-1", 10L);

        assertThat(result).extracting(DiagnosisCriterion::findingTermCode)
            .containsExactly("FEVER", "COUGH"); // id 升序；排除其他版本/租户
    }

    @Test
    void deleteByTenantIdAndIdIsTenantScoped() {
        DiagnosisCriterion saved = repository.save(criterion("t-1", 10L, "FEVER", DiagnosisDirection.REQUIRED));

        repository.deleteByTenantIdAndId("t-2", saved.id()); // 跨租户删除不命中
        assertThat(repository.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).hasSize(1);

        repository.deleteByTenantIdAndId("t-1", saved.id()); // 本租户删除命中
        assertThat(repository.findByTenantIdAndDiagnosisVersionId("t-1", 10L)).isEmpty();
    }

    private DiagnosisCriterion criterion(String tenantId, Long versionId, String code, DiagnosisDirection dir) {
        Instant now = Instant.now();
        return new DiagnosisCriterion(null, tenantId, versionId, code, dir, DiagnosisWeight.MAJOR,
            null, null, null, now, "tester", now, "tester", "trace-dx");
    }
}
