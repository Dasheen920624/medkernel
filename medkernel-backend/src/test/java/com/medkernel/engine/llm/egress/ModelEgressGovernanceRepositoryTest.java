package com.medkernel.engine.llm.egress;

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
 * 出域治理三表（白名单 / 审批 / 证据）数据访问回归测试（LLM-03）。
 *
 * <p>验证 V125 迁移在 H2(PostgreSQL 模式) 下建表成功，三仓储读写与按租户+能力码的唯一检索口径正确。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:model-egress-governance-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ModelEgressGovernanceRepositoryTest {

    @Autowired
    ModelEgressWhitelistRepository whitelistRepository;

    @Autowired
    ModelEgressApprovalRepository approvalRepository;

    @Autowired
    ModelEgressEvidenceRepository evidenceRepository;

    @Test
    void whitelist_savesAndQueriesByTenantAndCapability() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        whitelistRepository.save(new ModelEgressWhitelist(
            null, "tenant-1", "knowledge.extract",
            "[\"clinicalText\"]", "HIGH",
            "{\"clinicalText\":\"GENERALIZE\"}", "MEDIUM", "Y",
            now, "tester", now, "tester"));

        assertThat(whitelistRepository.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .isPresent()
            .get()
            .satisfies(saved -> {
                assertThat(saved.allowedFields()).isEqualTo("[\"clinicalText\"]");
                assertThat(saved.sensitivityLevel()).isEqualTo("HIGH");
                assertThat(saved.desensitizationRules()).isEqualTo("{\"clinicalText\":\"GENERALIZE\"}");
                assertThat(saved.approvalThresholdLevel()).isEqualTo("MEDIUM");
                assertThat(saved.guardrailLockedFlag()).isEqualTo("Y");
            });
    }

    @Test
    void approval_findsLatestApprovedByPayloadHash() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        approvalRepository.save(new ModelEgressApproval(
            null, "tenant-1", "knowledge.extract", "hash-abc",
            "APPROVED", "compliance-001", now,
            now, "compliance-001", now, "compliance-001"));

        assertThat(approvalRepository
                .findFirstByTenantIdAndCapabilityCodeAndPayloadHashAndStatusOrderByIdDesc(
                    "tenant-1", "knowledge.extract", "hash-abc", "APPROVED"))
            .isPresent()
            .get()
            .extracting(ModelEgressApproval::approver)
            .isEqualTo("compliance-001");
    }

    @Test
    void evidence_persistsEgressTrace() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        ModelEgressEvidence saved = evidenceRepository.save(new ModelEgressEvidence(
            null, "tenant-1", "knowledge.extract", "task-xyz",
            "[\"clinicalText\"]", "sha256-of-masked", 7L, "ollama-local",
            now, "system", now, "system"));

        assertThat(saved.id()).isNotNull();
        assertThat(evidenceRepository.findById(saved.id()))
            .isPresent()
            .get()
            .satisfies(e -> {
                assertThat(e.desensitizedHash()).isEqualTo("sha256-of-masked");
                assertThat(e.approvalId()).isEqualTo(7L);
            });
    }
}
