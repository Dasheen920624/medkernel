package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:rule-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class RuleRepositoryTest {

    @Autowired RuleDefinitionRepository definitions;
    @Autowired RuleVersionRepository versions;
    @Autowired RuleTestCaseRepository testCases;
    @Autowired RuleExecutionLogRepository executions;
    @Autowired RuleOverrideLogRepository overrides;
    @Autowired RuleApplicabilityRepository applicabilities;
    @Autowired RuleGovernanceRepository governance;
    @Autowired RuleSignoffRepository signoffs;

    @AfterEach
    void wipe() {
        signoffs.deleteAll();
        governance.deleteAll();
        overrides.deleteAll();
        executions.deleteAll();
        testCases.deleteAll();
        applicabilities.deleteAll();
        versions.deleteAll();
        definitions.deleteAll();
    }

    @Test
    void ruleDefinitionExposesInteractionGovernanceFields() {
        assertThat(Arrays.stream(RuleDefinition.class.getRecordComponents())
            .map(component -> component.getName())
            .toList())
            .contains("priority", "suppressedBy", "dedupeWindowSeconds");
    }

    @Test
    void persistsRuleDefinitionVersionTestCaseAndExecutionLog() {
        String ruleId = "rule-" + UUID.randomUUID();
        String versionId = "rv-" + UUID.randomUUID();
        String caseId = "rtc-" + UUID.randomUUID();
        String executionId = "rex-" + UUID.randomUUID();

        RuleDefinition savedRule = definitions.save(sampleRule(ruleId, "tenant-A", "RULE.ANTICOAG"));
        RuleVersion savedVersion = versions.save(sampleVersion(versionId, "tenant-A", ruleId));
        RuleApplicability savedApplicability = applicabilities.save(new RuleApplicability(
            null, versionId, "tenant-A", "{}", "{}",
            "[\"INPATIENT\",\"ED\"]", LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 12, 31), 25, Instant.now(), "tester",
            Instant.now(), "tester", "trace-rule"));
        RuleTestCase savedCase = testCases.save(sampleCase(caseId, "tenant-A", ruleId, versionId));
        RuleExecutionLog savedExecution = executions.save(sampleExecution(executionId, "tenant-A", ruleId, versionId));

        assertThat(savedRule.id()).isNotNull();
        assertThat(savedVersion.id()).isNotNull();
        assertThat(savedApplicability.id()).isNotNull();
        assertThat(savedCase.id()).isNotNull();
        assertThat(savedExecution.id()).isNotNull();

        assertThat(definitions.findByRuleIdAndTenantId(ruleId, "tenant-A")).isPresent();
        assertThat(versions.findByVersionIdAndTenantId(versionId, "tenant-A")).isPresent();
        assertThat(applicabilities.findByTenantIdAndRuleVersionId("tenant-A", versionId))
            .get()
            .extracting(RuleApplicability::rolloutPercent)
            .isEqualTo(25);
        assertThat(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(versionId, "tenant-A"))
            .extracting(RuleTestCase::caseId)
            .containsExactly(caseId);
        assertThat(executions.findByExecutionIdAndTenantId(executionId, "tenant-A")).isPresent();
    }

    @Test
    void persistsGovernanceAndDistinctSignoffEvidence() {
        String ruleId = "rule-" + UUID.randomUUID();
        String versionId = "rv-" + UUID.randomUUID();
        Instant now = Instant.now();
        definitions.save(sampleRule(ruleId, "tenant-A", "RULE.GOVERNED"));
        versions.save(sampleVersion(versionId, "tenant-A", ruleId));

        RuleGovernance savedGovernance = governance.save(new RuleGovernance(
            null,
            "rg-" + UUID.randomUUID(),
            "tenant-A",
            versionId,
            RuleGovernanceState.COMMITTEE,
            2,
            1,
            "author-1",
            "同行评审已完成",
            now,
            "author-1",
            now,
            "reviewer-1",
            "trace-governance",
            null
        ));
        RuleSignoff savedSignoff = signoffs.save(new RuleSignoff(
            null,
            "rs-" + UUID.randomUUID(),
            "tenant-A",
            versionId,
            RuleSignoffStage.COMMITTEE,
            1,
            "medical-affairs",
            "reviewer-2",
            RuleSignoffDecision.APPROVED,
            "同意进入影子验证",
            now,
            "trace-governance"
        ));

        assertThat(savedGovernance.id()).isNotNull();
        assertThat(savedSignoff.id()).isNotNull();
        assertThat(governance.findByRuleVersionIdAndTenantId(versionId, "tenant-A"))
            .get()
            .extracting(RuleGovernance::state)
            .isEqualTo(RuleGovernanceState.COMMITTEE);
        assertThat(signoffs.findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
            versionId, "tenant-A"))
            .extracting(RuleSignoff::signerId)
            .containsExactly("reviewer-2");
        assertThat(governance.findByRuleVersionIdAndTenantId(versionId, "tenant-B")).isEmpty();
    }

    @Test
    void concurrentGovernanceUpdatesCannotOverwriteEachOther() {
        String ruleId = "rule-" + UUID.randomUUID();
        String versionId = "rv-" + UUID.randomUUID();
        Instant now = Instant.now();
        definitions.save(sampleRule(ruleId, "tenant-A", "RULE.CONCURRENT"));
        versions.save(sampleVersion(versionId, "tenant-A", ruleId));
        governance.save(new RuleGovernance(
            null,
            "rg-" + UUID.randomUUID(),
            "tenant-A",
            versionId,
            RuleGovernanceState.COMMITTEE,
            2,
            1,
            "author-1",
            "等待委员会会签",
            now,
            "author-1",
            now,
            "reviewer-1",
            "trace-governance",
            null
        ));
        RuleGovernance first = governance.findByRuleVersionIdAndTenantId(versionId, "tenant-A")
            .orElseThrow();
        RuleGovernance stale = governance.findByRuleVersionIdAndTenantId(versionId, "tenant-A")
            .orElseThrow();

        governance.save(first.transition(
            RuleGovernanceState.SHADOW,
            "会签完成",
            now.plusSeconds(1),
            "publisher-1",
            "trace-first"
        ));

        assertThatThrownBy(() -> governance.save(stale.reject(
                "并发驳回",
                now.plusSeconds(2),
                "reviewer-2",
                "trace-stale"
            )))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void repositoryQueriesDoNotLeakAcrossTenants() {
        String ruleId = "rule-" + UUID.randomUUID();
        definitions.save(sampleRule(ruleId, "tenant-A", "RULE.ISOLATED"));

        Optional<RuleDefinition> wrongTenant = definitions.findByRuleIdAndTenantId(ruleId, "tenant-B");

        assertThat(wrongTenant).isEmpty();
    }

    @Test
    void findsRecentSuccessfulExecutionByPatientAndSemanticKey() {
        String ruleId = "rule-" + UUID.randomUUID();
        String versionId = "rv-" + UUID.randomUUID();
        String executionId = "rex-" + UUID.randomUUID();
        definitions.save(sampleRule(ruleId, "tenant-A", "RULE.ANTICOAG"));
        versions.save(sampleVersion(versionId, "tenant-A", ruleId));
        executions.save(sampleExecution(executionId, "tenant-A", ruleId, versionId));

        Optional<RuleExecutionLog> recent = executions.findRecentSuccessful(
            "tenant-A", "MPI-1", "RULE.ANTICOAG:STRONG_REMINDER",
            Instant.now().minusSeconds(60));

        assertThat(recent).isPresent();
        assertThat(recent.orElseThrow().executionId()).isEqualTo(executionId);
    }

    @Test
    void persistsOverrideAgainstRealExecution() {
        String ruleId = "rule-" + UUID.randomUUID();
        String versionId = "rv-" + UUID.randomUUID();
        String executionId = "rex-" + UUID.randomUUID();
        definitions.save(sampleRule(ruleId, "tenant-A", "RULE.ANTICOAG"));
        versions.save(sampleVersion(versionId, "tenant-A", ruleId));
        executions.save(sampleExecution(executionId, "tenant-A", ruleId, versionId));
        Instant now = Instant.now();

        RuleOverrideLog saved = overrides.save(new RuleOverrideLog(
            null, "rov-" + UUID.randomUUID(), "tenant-A", executionId, ruleId, versionId,
            "MPI-1", "ENC-1", RuleActionCode.STRONG_REMINDER, "已完成临床复核",
            "doctor-1", now, now, "trace-rule"));

        assertThat(saved.id()).isNotNull();
        assertThat(overrides.findByTenantIdAndExecutionIdAndActionCode(
            "tenant-A", executionId, RuleActionCode.STRONG_REMINDER)).isPresent();
    }

    @Test
    void pagesRulesByStatusTypeAndRisk() {
        definitions.save(sampleRule("rule-low", "tenant-A", "RULE.LOW"));
        definitions.save(sampleRule("rule-high", "tenant-A", "RULE.HIGH"));
        definitions.save(sampleRule("rule-other", "tenant-B", "RULE.HIGH"));

        long total = definitions.countByFilter("tenant-A", "DRAFT", "ORDER", null);
        List<RuleDefinition> rows = definitions.pageByFilter("tenant-A", "DRAFT", "ORDER", null, 0, 10);

        assertThat(total).isEqualTo(2);
        assertThat(rows).extracting(RuleDefinition::tenantId).containsOnly("tenant-A");
        assertThat(rows).extracting(RuleDefinition::ruleType).containsOnly(RuleType.ORDER);
    }

    private RuleDefinition sampleRule(String ruleId, String tenantId, String ruleCode) {
        Instant now = Instant.now();
        return new RuleDefinition(
            null, ruleId, tenantId, ruleCode, "抗凝风险提示", RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 0, RuleDefinitionStatus.DRAFT,
            null, "rpv-1", "dept-1", now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion sampleVersion(String versionId, String tenantId, String ruleId) {
        Instant now = Instant.now();
        return new RuleVersion(
            null, versionId, tenantId, ruleId, 1, "院内抗凝用药管理规范 2026",
            "初始版本", "{\"trigger\":\"order-sign\",\"when\":{\"all\":[]},\"then\":[],\"explain\":{}}",
            "{\"title\":\"抗凝风险提示\"}", RuleVersionStatus.DRAFT,
            null, null, null, now, "tester", now, "tester", "trace-rule");
    }

    private RuleTestCase sampleCase(String caseId, String tenantId, String ruleId, String versionId) {
        Instant now = Instant.now();
        return new RuleTestCase(
            null, caseId, tenantId, ruleId, versionId, RuleTestCaseType.POSITIVE,
            "ctx-1", "{\"patient\":{\"age\":72}}", true, RuleRiskLevel.HIGH, "STRONG_REMINDER",
            null, null, null, null, now, "tester", now, "tester", "trace-rule");
    }

    private RuleExecutionLog sampleExecution(String executionId, String tenantId, String ruleId, String versionId) {
        Instant now = Instant.now();
        return new RuleExecutionLog(
            null, executionId, tenantId, ruleId, versionId, "order-sign", "evt-1", "tester",
            "MPI-1", "ENC-1", "RULE.ANTICOAG:STRONG_REMINDER", "sha256:abc", true,
            RuleRiskLevel.HIGH, "[{\"actionCode\":\"STRONG_REMINDER\"}]",
            "{\"title\":\"抗凝风险提示\"}", RuleExecutionStatus.SUCCESS,
            null, null, null, now, now, "trace-rule");
    }
}
