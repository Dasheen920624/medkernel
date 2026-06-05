package com.medkernel.engine.quality.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.evaluation.QualityFinding;
import com.medkernel.engine.evaluation.QualityFindingRepository;
import com.medkernel.engine.evaluation.QualityFindingSeverity;
import com.medkernel.engine.evaluation.QualityFindingStatus;
import com.medkernel.engine.evaluation.RectificationReview;
import com.medkernel.engine.evaluation.RectificationReviewDecision;
import com.medkernel.engine.evaluation.RectificationReviewRepository;
import com.medkernel.engine.evaluation.RectificationTask;
import com.medkernel.engine.evaluation.RectificationTaskRepository;
import com.medkernel.engine.evaluation.RectificationTaskStatus;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.recommendation.RecommendationCard;
import com.medkernel.engine.recommendation.RecommendationCardRepository;
import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationTrigger;
import com.medkernel.engine.recommendation.RecommendationTriggerRepository;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@Import({ValueMetricsService.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:value-metrics-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ValueMetricsServiceTest {

    @Autowired ValueMetricsService service;
    @Autowired RecommendationTriggerRepository triggers;
    @Autowired RecommendationCardRepository cards;
    @Autowired PatientPathwayRepository patientPathways;
    @Autowired QualityFindingRepository findings;
    @Autowired RectificationTaskRepository tasks;
    @Autowired RectificationReviewRepository reviews;

    @AfterEach
    void wipe() {
        RequestContext.clear();
        reviews.deleteAll();
        tasks.deleteAll();
        findings.deleteAll();
        cards.deleteAll();
        triggers.deleteAll();
        patientPathways.deleteAll();
    }

    @Test
    void aggregatesSixVersionedMetricsFromRealRuntimeFacts() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedRecommendation("tenant-A", "card-accepted", RecommendationCardStatus.ACCEPTED, now);
        seedRecommendation("tenant-A", "card-rejected", RecommendationCardStatus.REJECTED, now.plusSeconds(60));
        seedFinding("tenant-A", "qf-closed", "FIND.CLOSED", QualityFindingStatus.CLOSED, "dept-1", now);
        seedFinding("tenant-A", "qf-waived", "FIND.WAIVED", QualityFindingStatus.WAIVED, "dept-1", now.plusSeconds(60));
        seedTask("tenant-A", "rt-closed", "qf-closed", RectificationTaskStatus.CLOSED, "dept-1", now);
        seedTask("tenant-A", "rt-open", "qf-waived", RectificationTaskStatus.ASSIGNED, "dept-1", now.plusSeconds(60));
        seedReview("tenant-A", "rr-waived", "qf-waived", "rt-open", RectificationReviewDecision.WAIVED, now);
        seedPatientPathway("tenant-A", "pp-done", PatientPathwayStatus.COMPLETED, now);
        seedPatientPathway("tenant-A", "pp-exit", PatientPathwayStatus.EXITED, now.plusSeconds(60));
        seedFinding("tenant-A", "qf-missed", "MISSED.CDSS.001", QualityFindingStatus.ASSIGNED, "dept-2", now);

        ValueMetricSummaryResponse response = withTenant("tenant-A",
            () -> service.summary(new ValueMetricFilter(null, null, null)));

        assertThat(response.metrics()).hasSize(6);
        assertMetric(response, ValueMetricCode.ADOPTION_RATE, ValueMetricStatus.AVAILABLE, 1, 2, "0.5000");
        assertMetric(response, ValueMetricCode.FALSE_POSITIVE_RATE, ValueMetricStatus.AVAILABLE, 1, 2, "0.5000");
        assertMetric(response, ValueMetricCode.MISSED_CASE_RETROSPECTIVE, ValueMetricStatus.AVAILABLE, 1, 1, "1.0000");
        assertMetric(response, ValueMetricCode.PATHWAY_COMPLETION_RATE, ValueMetricStatus.AVAILABLE, 1, 2, "0.5000");
        assertMetric(response, ValueMetricCode.RECTIFICATION_CLOSURE_RATE, ValueMetricStatus.AVAILABLE, 1, 2, "0.5000");
        assertMetric(response, ValueMetricCode.INSURANCE_VIOLATION_REDUCTION,
            ValueMetricStatus.NOT_AVAILABLE, null, null, null);
        assertThat(metric(response, ValueMetricCode.ADOPTION_RATE).formulaVersion()).isEqualTo("OPT-08.v1");
        assertThat(metric(response, ValueMetricCode.INSURANCE_VIOLATION_REDUCTION).explanation())
            .contains("医保违规事实源未接入");
    }

    @Test
    void drilldownListsMissedCasesWithoutCrossTenantLeakage() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedFinding("tenant-A", "qf-missed-a", "MISSED.CDSS.001", QualityFindingStatus.ASSIGNED, "dept-1", now);
        seedFinding("tenant-B", "qf-missed-b", "MISSED.CDSS.001", QualityFindingStatus.ASSIGNED, "dept-1", now);

        ValueMetricDrilldownResponse response = withTenant("tenant-A",
            () -> service.drilldown(ValueMetricCode.MISSED_CASE_RETROSPECTIVE,
                new ValueMetricFilter(null, null, null), 0, 20));

        assertThat(response.metric().metricCode()).isEqualTo(ValueMetricCode.MISSED_CASE_RETROSPECTIVE);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("quality_finding");
            assertThat(item.sourceId()).isEqualTo("qf-missed-a");
            assertThat(item.departmentId()).isEqualTo("dept-1");
            assertThat(item.reason()).contains("漏报回溯");
        });
    }

    @Test
    void campusScopeWithoutSourceDimensionIsNotAvailable() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedRecommendation("tenant-A", "card-accepted", RecommendationCardStatus.ACCEPTED, now);
        seedRecommendation("tenant-A", "card-rejected", RecommendationCardStatus.REJECTED, now.plusSeconds(60));
        seedFinding("tenant-A", "qf-waived", "FIND.WAIVED", QualityFindingStatus.WAIVED, "dept-1", now);

        ValueMetricSummaryResponse response = withTenant("tenant-A",
            () -> service.summary(new ValueMetricFilter(null, null, null, null, "campus-1")));

        assertThat(response.metrics())
            .extracting(ValueMetricResponse::status)
            .containsOnly(ValueMetricStatus.NOT_AVAILABLE);
        assertThat(metric(response, ValueMetricCode.ADOPTION_RATE).explanation())
            .contains("院区");
    }

    private static void assertMetric(
            ValueMetricSummaryResponse response,
            ValueMetricCode code,
            ValueMetricStatus status,
            Integer numerator,
            Integer denominator,
            String value) {
        ValueMetricResponse metric = metric(response, code);
        assertThat(metric.status()).isEqualTo(status);
        if (numerator == null) {
            assertThat(metric.numerator()).isNull();
            assertThat(metric.denominator()).isNull();
            assertThat(metric.value()).isNull();
            return;
        }
        assertThat(metric.numerator()).isEqualTo(new BigDecimal(numerator));
        assertThat(metric.denominator()).isEqualTo(new BigDecimal(denominator));
        assertThat(metric.value()).isEqualByComparingTo(value);
    }

    private static ValueMetricResponse metric(ValueMetricSummaryResponse response, ValueMetricCode code) {
        return response.metrics().stream()
            .filter(metric -> metric.metricCode() == code)
            .findFirst()
            .orElseThrow();
    }

    private void seedRecommendation(
            String tenantId, String cardId, RecommendationCardStatus status, Instant createdAt) {
        String triggerId = "rt-" + UUID.randomUUID();
        triggers.save(new RecommendationTrigger(
            null, triggerId, tenantId, "TRG." + triggerId, "order-sign",
            "event-1", "snapshot-1", "patient-1", "enc-1", "pathway-1",
            "WARD_ORDER", "1.0.0", "sha256:trigger", RecommendationTriggerStatus.EVALUATED,
            null, createdAt, createdAt, "tester", createdAt, "tester", "trace-recommendation"));
        cards.save(new RecommendationCard(
            null, cardId, tenantId, triggerId, "CARD." + cardId, RecommendationCardType.MEDICATION,
            "抗凝用药风险提醒", "患者当前医嘱满足抗凝风险规则", "请确认出血风险评估",
            RecommendationRiskLevel.HIGH, RecommendationInterruptLevel.WEAK_INTERRUPTIVE,
            status, true, false, "来源：抗凝用药规则 v1", "{\"reason\":\"规则命中\"}",
            "WARD_ORDER:ANTICOAG", createdAt.plusSeconds(3600),
            createdAt, "tester", createdAt, "tester", "trace-recommendation",
            "builtin-risk-baseline", "baseline", CdssAutomationLevel.INTERRUPTIVE,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 72, "OPT04_SILENT_TRIAL",
            false, "NMPA_RESERVED", "TRACEABLE_EVIDENCE_REQUIRED", "高危 CDSS 输出必须医师确认"));
    }

    private void seedFinding(
            String tenantId, String findingId, String findingCode, QualityFindingStatus status,
            String departmentId, Instant createdAt) {
        findings.save(new QualityFinding(
            null, findingId, tenantId, "run-" + findingId, "result-" + findingId, "indicator-1", findingCode,
            "漏报回溯问题", "后续质控复盘识别到原运行链路未提示该病例",
            QualityFindingSeverity.P1, status, "漏报回溯：病历证据 review-1", departmentId,
            createdAt.plusSeconds(86400), createdAt, "qa-1", createdAt, "qa-1", "trace-evaluation"));
    }

    private void seedTask(
            String tenantId, String taskId, String findingId, RectificationTaskStatus status,
            String departmentId, Instant createdAt) {
        tasks.save(new RectificationTask(
            null, taskId, tenantId, findingId, departmentId, "head-1", status,
            createdAt.plusSeconds(86400), "补录评估并复核流程", "rect-evidence-1",
            status == RectificationTaskStatus.CLOSED ? createdAt : null,
            status == RectificationTaskStatus.CLOSED ? "head-1" : null,
            status == RectificationTaskStatus.CLOSED ? createdAt.plusSeconds(3600) : null,
            createdAt, "qa-1", createdAt, "qa-1", "trace-evaluation"));
    }

    private void seedReview(
            String tenantId, String reviewId, String findingId, String taskId,
            RectificationReviewDecision decision, Instant reviewedAt) {
        reviews.save(new RectificationReview(
            null, reviewId, tenantId, findingId, taskId, decision,
            "证据不足，按误报豁免", "review-evidence-1", "qa-1", reviewedAt,
            reviewedAt, "qa-1", reviewedAt, "qa-1", "trace-evaluation"));
    }

    private void seedPatientPathway(
            String tenantId, String patientPathwayId, PatientPathwayStatus status, Instant enteredAt) {
        Instant completedAt = status == PatientPathwayStatus.COMPLETED ? enteredAt.plusSeconds(3600) : null;
        Instant exitedAt = status == PatientPathwayStatus.EXITED ? enteredAt.plusSeconds(3600) : null;
        patientPathways.save(new PatientPathway(
            null, patientPathwayId, tenantId, "patient-1", "enc-1", "template-1",
            "ASSESS", status, enteredAt, completedAt, exitedAt, null, null,
            enteredAt, "tester", enteredAt, "tester", "trace-pathway"));
    }

    private static <T> T withTenant(String tenantId, ThrowingSupplier<T> supplier) {
        try {
            return RequestContext.callWith(
                new RequestContext.Snapshot("trace-value", OrgScope.tenant(tenantId), "qa-1"),
                supplier::get);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
