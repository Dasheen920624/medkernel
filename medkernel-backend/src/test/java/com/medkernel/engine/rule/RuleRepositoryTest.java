package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

    @AfterEach
    void wipe() {
        executions.deleteAll();
        testCases.deleteAll();
        versions.deleteAll();
        definitions.deleteAll();
    }

    @Test
    void persistsRuleDefinitionVersionTestCaseAndExecutionLog() {
        String ruleId = "rule-" + UUID.randomUUID();
        String versionId = "rv-" + UUID.randomUUID();
        String caseId = "rtc-" + UUID.randomUUID();
        String executionId = "rex-" + UUID.randomUUID();

        RuleDefinition savedRule = definitions.save(sampleRule(ruleId, "tenant-A", "RULE.ANTICOAG"));
        RuleVersion savedVersion = versions.save(sampleVersion(versionId, "tenant-A", ruleId));
        RuleTestCase savedCase = testCases.save(sampleCase(caseId, "tenant-A", ruleId, versionId));
        RuleExecutionLog savedExecution = executions.save(sampleExecution(executionId, "tenant-A", ruleId, versionId));

        assertThat(savedRule.id()).isNotNull();
        assertThat(savedVersion.id()).isNotNull();
        assertThat(savedCase.id()).isNotNull();
        assertThat(savedExecution.id()).isNotNull();

        assertThat(definitions.findByRuleIdAndTenantId(ruleId, "tenant-A")).isPresent();
        assertThat(versions.findByVersionIdAndTenantId(versionId, "tenant-A")).isPresent();
        assertThat(testCases.findByVersionIdAndTenantIdOrderByCreatedAtAsc(versionId, "tenant-A"))
            .extracting(RuleTestCase::caseId)
            .containsExactly(caseId);
        assertThat(executions.findByExecutionIdAndTenantId(executionId, "tenant-A")).isPresent();
    }

    @Test
    void repositoryQueriesDoNotLeakAcrossTenants() {
        String ruleId = "rule-" + UUID.randomUUID();
        definitions.save(sampleRule(ruleId, "tenant-A", "RULE.ISOLATED"));

        Optional<RuleDefinition> wrongTenant = definitions.findByRuleIdAndTenantId(ruleId, "tenant-B");

        assertThat(wrongTenant).isEmpty();
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
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, RuleDefinitionStatus.DRAFT,
            null, "rpv-1", "dept-1", now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion sampleVersion(String versionId, String tenantId, String ruleId) {
        Instant now = Instant.now();
        return new RuleVersion(
            null, versionId, tenantId, ruleId, 1, "院内抗凝用药管理规范 2026",
            "初始版本", "{\"trigger\":\"ORDER_SIGN\",\"when\":{\"all\":[]},\"then\":[],\"explain\":{}}",
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
            null, executionId, tenantId, ruleId, versionId, "ORDER_SIGN", "evt-1", "tester",
            "sha256:abc", true, RuleRiskLevel.HIGH, "[{\"actionCode\":\"STRONG_REMINDER\"}]",
            "{\"title\":\"抗凝风险提示\"}", RuleExecutionStatus.SUCCESS,
            null, null, now, now, "trace-rule");
    }
}
