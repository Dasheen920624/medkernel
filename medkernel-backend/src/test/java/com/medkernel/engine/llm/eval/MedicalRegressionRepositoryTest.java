package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

/**
 * 医学回归评测运行、基准用例和逐例证据三表仓储回归测试（LLM-07，V151 五方言迁移）。
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

    @Autowired
    ModelEvalCaseEvidenceRepository evidenceRepo;

    @Test
    void casesQueriedByCapabilityAndEnabled() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        caseRepo.save(new MedicalRegressionCase(null, "tenant-1", "knowledge.extract",
            "general", "阿司匹林禁忌", "活动性出血禁用", "[]", "[]", 100,
            "CONTRAINDICATION", "source-version:1", "Y",
            "v1", "Y", now, "system", now, "system"));

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
            "rule",
            "红线ID：redline-dose-limit\n证据引用：source-version:77#dose-limit",
            "儿童用药剂量上限需双签", "[]", "[]", 100,
            "DOSE_LIMIT", "source-version:77#dose-limit", "Y",
            "2026.1", "Y", now, "system", now, "system"));

        assertThat(caseRepo.findByTenantIdAndCapabilityCodeAndCaseInput(
            "tenant-1", "rule.draft", "红线ID：redline-dose-limit\n证据引用：source-version:77#dose-limit"))
            .isPresent();
        assertThat(caseRepo.findByTenantIdAndCapabilityCodeAndCaseInput(
            "tenant-1", "rule.draft", "红线ID：other"))
            .isEmpty();
    }

    @Test
    void caseStoresLongestClinicalRedLineType() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        caseRepo.save(new MedicalRegressionCase(null, "tenant-1", "rule.draft",
            "rule",
            "特殊人群禁忌证红线", "特殊人群禁忌证必须阻断",
            "[]", "[]", 100,
            "SPECIAL_POPULATION_CONTRAINDICATION", "source-version:77#special-population", "Y",
            "2026.1", "Y", now, "system", now, "system"));

        assertThat(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .extracting(MedicalRegressionCase::redLineType)
            .contains("SPECIAL_POPULATION_CONTRAINDICATION");
    }

    @Test
    void evalRunFoundByProviderVersionCapabilityAndStatus() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        runRepo.save(new ModelEvalRun(null, "tenant-1", "claude-prod", "claude-opus-4-8",
            "rule.draft", "prompt:v1", "tool:v1",
            10, 10, 0, null, null, "N", "N", "N", "PASSED", "[]",
            "逐例证据已核查并确认可放行。", "quality-001", now, now, "system", now, "system"));

        assertThat(runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndStatusOrderByIdDesc(
                "tenant-1", "claude-prod", "claude-opus-4-8", "PASSED"))
            .isPresent()
            .get()
            .extracting(ModelEvalRun::passedCases)
            .isEqualTo(10);
        assertThat(runRepo
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                "tenant-1", "claude-prod", "claude-opus-4-8", "rule.draft", "PASSED"))
            .isPresent();
        assertThat(runRepo
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                "tenant-1", "claude-prod", "claude-opus-4-8", "pathway.draft", "PASSED"))
            .isEmpty();
    }

    @Test
    void pendingSignOffIsAtomicAndCannotOverwriteFirstReviewer() {
        Instant createdAt = Instant.parse("2026-06-14T00:00:00Z");
        ModelEvalRun pending = runRepo.save(new ModelEvalRun(
            null, "tenant-1", "ollama-local", "qwen2.5:0.5b",
            "rule.draft", "prompt:v1", "tool:v1",
            3, 3, 0, null, null, "N", "N", "N", "PENDING_REVIEW", "{}",
            null, null, null, createdAt, "author", createdAt, "author"));
        Instant firstSignedAt = Instant.parse("2026-06-14T00:01:00Z");
        Instant secondSignedAt = Instant.parse("2026-06-14T00:02:00Z");

        assertThat(runRepo.signOffPending(
            pending.id(), "tenant-1", "reviewer-1", "逐例证据核查通过，准予放行。", firstSignedAt))
            .isEqualTo(1);
        assertThat(runRepo.signOffPending(
            pending.id(), "tenant-1", "reviewer-2", "尝试覆盖首次复核意见。", secondSignedAt))
            .isZero();

        assertThat(runRepo.findById(pending.id()))
            .isPresent()
            .get()
            .satisfies(run -> {
                assertThat(run.status()).isEqualTo("PASSED");
                assertThat(run.reviewer()).isEqualTo("reviewer-1");
                assertThat(run.reviewComment()).isEqualTo("逐例证据核查通过，准予放行。");
                assertThat(run.signedAt()).isEqualTo(firstSignedAt);
            });
    }

    @Test
    void immutableCaseEvidenceIsTenantScopedAndRunListIsPaged() {
        Instant now = Instant.parse("2026-06-18T00:00:00Z");
        ModelEvalRun run = runRepo.save(new ModelEvalRun(
            null, "tenant-1", "mimo-external", "mimo-v2.5",
            "rule.draft", "prompt:v2", "tool:v3",
            1, 1, 0, null, null, "N", "N", "N", "PENDING_REVIEW", "{}",
            null, null, null, now, "quality-001", now, "quality-001"));
        evidenceRepo.save(new ModelEvalCaseEvidence(
            null, "tenant-1", run.id(), 101L, "2026.1", "活动性出血患者是否可用药？",
            "活动性出血禁用", "CONTRAINDICATION", "source-version:88#contraindication",
            "活动性出血禁用。来源：source-version:88#contraindication",
            "[\"source-version:88#contraindication\"]", "Y", "Y", "Y", "Y", "N", "Y",
            "[]", now, "quality-001"));

        assertThat(evidenceRepo.findByTenantIdAndRunIdOrderByIdAsc("tenant-1", run.id()))
            .singleElement()
            .satisfies(evidence -> {
                assertThat(evidence.passed()).isTrue();
                assertThat(evidence.outputContent()).contains("活动性出血禁用");
            });
        assertThat(evidenceRepo.findByTenantIdAndRunIdOrderByIdAsc("tenant-2", run.id())).isEmpty();
        assertThat(runRepo.findByTenantIdAndStatusOrderByCreatedAtDesc(
            "tenant-1", "PENDING_REVIEW", PageRequest.of(0, 1)))
            .extracting(ModelEvalRun::id)
            .containsExactly(run.id());
        assertThat(runRepo.countByTenantIdAndStatus("tenant-1", "PENDING_REVIEW")).isEqualTo(1);
    }
}
