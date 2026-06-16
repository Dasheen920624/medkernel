package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/**
 * 医学回归评测两表仓储回归测试（LLM-07，V126 五方言迁移）。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:medical-regression-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class MedicalRegressionRepositoryTest {

    @Autowired
    MedicalRegressionCaseRepository caseRepo;

    @Autowired
    ModelEvalRunRepository runRepo;

    @Test
    void casesQueriedByCapabilityAndEnabled() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        caseRepo.save(new MedicalRegressionCase(null, "tenant-1", "knowledge.extract",
            "阿司匹林禁忌", "活动性出血禁用", "CONTRAINDICATION", "Y", "v1", "Y",
            now, "system", now, "system"));

        assertThat(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "knowledge.extract", "Y"))
            .hasSize(1)
            .first()
            .satisfies(c -> {
                assertThat(c.redLine()).isTrue();
                assertThat(c.requiresCitation()).isTrue();
            });
    }

    @Test
    void caseInputCanBeUsedAsProjectionDeduplicationKey() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        caseRepo.save(new MedicalRegressionCase(null, "tenant-1", "rule.draft",
            "红线ID：redline-dose-limit\n证据引用：source-version:77#dose-limit",
            "儿童用药剂量上限需双签", "DOSE_LIMIT", "Y", "2026.1", "Y",
            now, "system", now, "system"));

        assertThat(caseRepo.findByTenantIdAndCapabilityCodeAndCaseInput(
            "tenant-1", "rule.draft", "红线ID：redline-dose-limit\n证据引用：source-version:77#dose-limit"))
            .isPresent();
        assertThat(caseRepo.findByTenantIdAndCapabilityCodeAndCaseInput(
            "tenant-1", "rule.draft", "红线ID：other"))
            .isEmpty();
    }

    @Test
    void evalRunFoundByProviderVersionStatus() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        runRepo.save(new ModelEvalRun(null, "tenant-1", "claude-prod", "claude-opus-4-8",
            10, 10, 0, "N", "N", "PASSED", "quality-001", now,
            now, "system", now, "system"));

        assertThat(runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndStatusOrderByIdDesc(
                "tenant-1", "claude-prod", "claude-opus-4-8", "PASSED"))
            .isPresent()
            .get()
            .extracting(ModelEvalRun::passedCases)
            .isEqualTo(10);
    }
}
