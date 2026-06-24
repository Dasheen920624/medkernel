package com.medkernel.engine.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
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
    "spring.datasource.url=jdbc:h2:mem:evaluation-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class EvaluationRepositoryTest {

    @Autowired EvaluationIndicatorRepository indicators;
    @Autowired EvaluationRunRepository runs;
    @Autowired EvaluationResultRepository results;
    @Autowired QualityFindingRepository findings;
    @Autowired RectificationTaskRepository tasks;
    @Autowired RectificationReviewRepository reviews;

    @AfterEach
    void wipe() {
        reviews.deleteAll();
        tasks.deleteAll();
        findings.deleteAll();
        results.deleteAll();
        runs.deleteAll();
        indicators.deleteAll();
    }

    @Test
    void persistsEvaluationQualityClosedLoopFacts() {
        String indicatorId = "ei-" + UUID.randomUUID();
        String runId = "er-" + UUID.randomUUID();
        String resultId = "ers-" + UUID.randomUUID();
        String findingId = "qf-" + UUID.randomUUID();
        String taskId = "rt-" + UUID.randomUUID();

        EvaluationIndicator savedIndicator = indicators.save(sampleIndicator(indicatorId, "tenant-A"));
        EvaluationRun savedRun = runs.save(sampleRun(runId, "tenant-A"));
        EvaluationResult savedResult = results.save(sampleResult(resultId, "tenant-A", runId, indicatorId));
        QualityFinding savedFinding =
            findings.save(sampleFinding(findingId, "tenant-A", runId, resultId, indicatorId));
        RectificationTask savedTask = tasks.save(sampleTask(taskId, "tenant-A", findingId));
        RectificationReview savedReview =
            reviews.save(sampleReview("rr-" + UUID.randomUUID(), "tenant-A", findingId, taskId));

        assertThat(savedIndicator.id()).isNotNull();
        assertThat(savedRun.id()).isNotNull();
        assertThat(savedResult.id()).isNotNull();
        assertThat(savedFinding.id()).isNotNull();
        assertThat(savedTask.id()).isNotNull();
        assertThat(savedReview.id()).isNotNull();

        assertThat(indicators.findByIndicatorIdAndTenantId(indicatorId, "tenant-A")).isPresent();
        assertThat(runs.findByRunIdAndTenantId(runId, "tenant-A")).isPresent();
        assertThat(results.findByRunIdAndTenantIdOrderByCreatedAtAsc(runId, "tenant-A"))
            .extracting(EvaluationResult::resultLevel)
            .containsExactly(EvaluationResultLevel.NON_COMPLIANT);
        assertThat(findings.findByResultIdAndTenantIdOrderByCreatedAtAsc(resultId, "tenant-A"))
            .extracting(QualityFinding::severity)
            .containsExactly(QualityFindingSeverity.P1);
        assertThat(tasks.findByFindingIdAndTenantId(findingId, "tenant-A")).isPresent();
        assertThat(reviews.findByFindingIdAndTenantIdOrderByReviewedAtAsc(findingId, "tenant-A"))
            .extracting(RectificationReview::decision)
            .containsExactly(RectificationReviewDecision.APPROVED);
    }

    @Test
    void repositoryQueriesDoNotLeakAcrossTenants() {
        String indicatorId = "ei-" + UUID.randomUUID();
        indicators.save(sampleIndicator(indicatorId, "tenant-A"));

        Optional<EvaluationIndicator> wrongTenant =
            indicators.findByIndicatorIdAndTenantId(indicatorId, "tenant-B");

        assertThat(wrongTenant).isEmpty();
    }

    private EvaluationIndicator sampleIndicator(String indicatorId, String tenantId) {
        Instant now = Instant.now();
        return new EvaluationIndicator(
            null, indicatorId, tenantId, "IND.VTE.PROPHYLAXIS", 1, "静脉血栓预防完成率",
            EvaluationSubjectType.MEDICAL_RECORD, "符合住院风险分层病例", "完成预防评估病例",
            "出血高风险除外", "达标率 >= 95%", "DISCHARGE+24H", "{\"department\":\"ward\"}",
            "dept-1", "guideline-1", EvaluationIndicatorStatus.ACTIVE,
            now, "qa-1", now, now, "qa-1", now, "qa-1", "trace-evaluation");
    }

    private EvaluationRun sampleRun(String runId, String tenantId) {
        Instant now = Instant.now();
        return new EvaluationRun(
            null, runId, tenantId, "RUN." + runId, EvaluationRunType.UPSTREAM_RESULT,
            "event-1", "snapshot-1", "patient-1", "enc-1", "DISCHARGE", null,
            "sha256:run", EvaluationRunStatus.RECORDED, null, now,
            now, "qa-1", now, "qa-1", "trace-evaluation");
    }

    private EvaluationResult sampleResult(
            String resultId, String tenantId, String runId, String indicatorId) {
        Instant now = Instant.now();
        return new EvaluationResult(
            null, resultId, tenantId, runId, indicatorId, "IND.VTE.PROPHYLAXIS", 1,
            EvaluationSubjectType.MEDICAL_RECORD, "record-1", new BigDecimal("70.5000"),
            EvaluationResultLevel.NON_COMPLIANT, true, "未完成风险评估", "evidence-1", "dept-1",
            now, "qa-1", now, "qa-1", "trace-evaluation");
    }

    private QualityFinding sampleFinding(
            String findingId, String tenantId, String runId, String resultId, String indicatorId) {
        Instant now = Instant.now();
        return new QualityFinding(
            null, findingId, tenantId, runId, resultId, indicatorId, "FIND.VTE.001",
            "未完成静脉血栓风险评估", "出院前未记录风险评估",
            QualityFindingSeverity.P1, QualityFindingStatus.ASSIGNED, "evidence-1", "dept-1",
            now.plusSeconds(86400), now, "qa-1", now, "qa-1", "trace-evaluation");
    }

    private RectificationTask sampleTask(String taskId, String tenantId, String findingId) {
        Instant now = Instant.now();
        return new RectificationTask(
            null, taskId, tenantId, findingId, "dept-1", "head-1",
            RectificationTaskStatus.SUBMITTED, now.plusSeconds(86400), "补录评估并复核流程",
            "rect-evidence-1", now, "head-1", null, now, "qa-1", now, "head-1",
            "trace-evaluation");
    }

    private RectificationReview sampleReview(
            String reviewId, String tenantId, String findingId, String taskId) {
        Instant now = Instant.now();
        return new RectificationReview(
            null, reviewId, tenantId, findingId, taskId, RectificationReviewDecision.APPROVED,
            "证据充分，整改完成", "review-evidence-1", "qa-1", now,
            now, "qa-1", now, "qa-1", "trace-evaluation");
    }
}
