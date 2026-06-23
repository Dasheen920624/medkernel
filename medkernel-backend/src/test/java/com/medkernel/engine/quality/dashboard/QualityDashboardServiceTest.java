package com.medkernel.engine.quality.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.evaluation.QualityFinding;
import com.medkernel.engine.evaluation.QualityFindingRepository;
import com.medkernel.engine.evaluation.QualityFindingSeverity;
import com.medkernel.engine.evaluation.QualityFindingStatus;
import com.medkernel.engine.evaluation.RectificationTask;
import com.medkernel.engine.evaluation.RectificationTaskRepository;
import com.medkernel.engine.evaluation.RectificationTaskStatus;
import com.medkernel.engine.quality.value.ValueMetricCode;
import com.medkernel.engine.quality.value.ValueMetricStatus;
import com.medkernel.engine.quality.value.ValueMetricsService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@Import({QualityDashboardService.class, ValueMetricsService.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:quality-dashboard-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class QualityDashboardServiceTest {

    @Autowired QualityDashboardService service;
    @Autowired QualityDashboardAlertRepository alerts;
    @Autowired QualityFindingRepository findings;
    @Autowired RectificationTaskRepository tasks;
    @Autowired RecommendationTriggerRepository triggers;
    @Autowired RecommendationCardRepository cards;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void wipe() {
        RequestContext.clear();
        alerts.deleteAll();
        tasks.deleteAll();
        findings.deleteAll();
        cards.deleteAll();
        triggers.deleteAll();
    }

    @Test
    void dashboardAggregatesRealFindingsTasksValueMetricsAndAlertsPerTenant() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedRecommendation("tenant-A", "card-accepted", RecommendationCardStatus.ACCEPTED, now);
        seedRecommendation("tenant-A", "card-rejected", RecommendationCardStatus.REJECTED, now.minusSeconds(60));
        seedFinding("tenant-A", "qf-critical", QualityFindingSeverity.P0,
            QualityFindingStatus.NEW, "dept-a", now.minusSeconds(3600));
        seedFinding("tenant-A", "qf-closed", QualityFindingSeverity.P2,
            QualityFindingStatus.CLOSED, "dept-b", now.minusSeconds(1800));
        seedFinding("tenant-A", "qf-waived", QualityFindingSeverity.P1,
            QualityFindingStatus.WAIVED, "dept-a", now.minusSeconds(1200));
        seedTask("tenant-A", "task-overdue", "qf-critical", RectificationTaskStatus.ASSIGNED,
            "dept-a", now.minusSeconds(86400), now.minusSeconds(3600));
        seedTask("tenant-A", "task-closed", "qf-closed", RectificationTaskStatus.CLOSED,
            "dept-b", now.plusSeconds(86400), now.minusSeconds(1800));
        seedFinding("tenant-B", "qf-other-tenant", QualityFindingSeverity.P0,
            QualityFindingStatus.NEW, "dept-a", now);

        QualityDashboardResponse response = withTenant("tenant-A",
            () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(1), null)));

        assertThat(response.summary().totalFindings()).isEqualTo(3);
        assertThat(response.summary().openFindings()).isEqualTo(1);
        assertThat(response.summary().closedFindings()).isEqualTo(1);
        assertThat(response.summary().waivedFindings()).isEqualTo(1);
        assertThat(response.summary().overdueRectificationTasks()).isEqualTo(1);
        assertThat(response.summary().activeAlerts()).isEqualTo(2);
        assertThat(response.heatmap()).hasSize(2);
        assertThat(response.heatmap()).anySatisfy(cell -> {
            assertThat(cell.departmentId()).isEqualTo("dept-a");
            assertThat(cell.totalFindings()).isEqualTo(2);
            assertThat(cell.maxSeverity()).isEqualTo(QualityFindingSeverity.P0.name());
            assertThat(cell.heatToken()).isEqualTo("QUALITY_HEAT_CRITICAL");
        });
        assertThat(response.valueMetrics().metrics()).anySatisfy(metric -> {
            assertThat(metric.metricCode()).isEqualTo(ValueMetricCode.ADOPTION_RATE);
            assertThat(metric.status()).isEqualTo(ValueMetricStatus.AVAILABLE);
            assertThat(metric.value()).isEqualByComparingTo(new BigDecimal("0.5000"));
        });
        assertThat(response.activeAlerts())
            .extracting(QualityDashboardAlertResponse::sourceId)
            .containsExactlyInAnyOrder("qf-critical", "task-overdue");
    }

    @Test
    void dashboardDepartmentFilterScopesSummaryHeatmapAndAlerts() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedFinding("tenant-A", "qf-critical", QualityFindingSeverity.P0,
            QualityFindingStatus.NEW, "dept-a", now);
        seedFinding("tenant-A", "qf-closed", QualityFindingSeverity.P2,
            QualityFindingStatus.CLOSED, "dept-b", now);
        seedTask("tenant-A", "task-overdue", "qf-critical", RectificationTaskStatus.ASSIGNED,
            "dept-a", now.minusSeconds(60), now);

        QualityDashboardResponse response = withTenant("tenant-A",
            () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(1), "dept-a")));

        assertThat(response.summary().totalFindings()).isEqualTo(1);
        assertThat(response.heatmap()).singleElement().satisfies(cell ->
            assertThat(cell.departmentId()).isEqualTo("dept-a"));
        assertThat(response.activeAlerts())
            .extracting(QualityDashboardAlertResponse::departmentId)
            .containsOnly("dept-a");
    }

    @Test
    void drilldownReturnsTraceableEvidencePackageForFindings() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedFinding("tenant-A", "qf-critical", QualityFindingSeverity.P0,
            QualityFindingStatus.NEW, "dept-a", now);

        QualityDashboardDrilldownResponse response = withTenant("tenant-A",
            () -> service.drilldown(
                new QualityDashboardFilter(null, now.plusSeconds(1), null),
                QualityDashboardDrilldownType.FINDING, 0, 20));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("quality_finding");
            assertThat(item.sourceId()).isEqualTo("qf-critical");
            assertThat(item.departmentId()).isEqualTo("dept-a");
            assertThat(item.evidenceSummary()).contains("病历证据");
            assertThat(item.traceId()).isEqualTo("trace-quality");
        });
        assertThat(response.evidencePackage().items()).singleElement().satisfies(item ->
            assertThat(item.sourceId()).isEqualTo("qf-critical"));
    }

    @Test
    void alertRefreshIsIdempotentAndAlertsEndpointFiltersOpenStatus() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedFinding("tenant-A", "qf-critical", QualityFindingSeverity.P1,
            QualityFindingStatus.ASSIGNED, "dept-a", now);
        seedTask("tenant-A", "task-overdue", "qf-critical", RectificationTaskStatus.ASSIGNED,
            "dept-a", now.minusSeconds(60), now);

        withTenant("tenant-A", () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(1), null)));
        withTenant("tenant-A", () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(1), null)));
        QualityDashboardAlertsResponse response = withTenant("tenant-A",
            () -> service.alerts(
                new QualityDashboardAlertFilter(null, now.plusSeconds(1), null,
                    QualityDashboardAlertStatus.OPEN, null),
                0, 20));

        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
            .extracting(QualityDashboardAlertResponse::status)
            .containsOnly(QualityDashboardAlertStatus.OPEN);
        assertThat(alerts.count()).isEqualTo(2);
    }

    @Test
    void alertsEndpointFiltersSeverityWithoutClientSidePagingDistortion() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedFinding("tenant-A", "qf-p0", QualityFindingSeverity.P0,
            QualityFindingStatus.ASSIGNED, "dept-a", now);
        seedFinding("tenant-A", "qf-p1", QualityFindingSeverity.P1,
            QualityFindingStatus.ASSIGNED, "dept-a", now);

        QualityDashboardAlertsResponse response = withTenant("tenant-A",
            () -> service.alerts(
                new QualityDashboardAlertFilter(null, now.plusSeconds(1), null,
                    QualityDashboardAlertStatus.OPEN, "P1"),
                0, 20));

        assertThat(response.items()).singleElement().satisfies(alert -> {
            assertThat(alert.sourceId()).isEqualTo("qf-p1");
            assertThat(alert.severity()).isEqualTo("P1");
        });
    }

    @Test
    void acknowledgeAlertKeepsStatusAcknowledgedAcrossReadModelRefresh() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedFinding("tenant-A", "qf-critical", QualityFindingSeverity.P1,
            QualityFindingStatus.ASSIGNED, "dept-a", now);

        withTenant("tenant-A", () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(1), null)));
        QualityDashboardAlertResponse acknowledged = withTenant("tenant-A",
            () -> service.acknowledgeAlert("HIGH_RISK_FINDING:quality_finding:qf-critical"));

        assertThat(acknowledged.status()).isEqualTo(QualityDashboardAlertStatus.ACKNOWLEDGED);

        QualityDashboardAlertsResponse afterRefresh = withTenant("tenant-A",
            () -> service.alerts(
                new QualityDashboardAlertFilter(null, now.plusSeconds(1), null,
                    QualityDashboardAlertStatus.ACKNOWLEDGED, null),
                0, 20));

        assertThat(afterRefresh.items()).singleElement().satisfies(alert -> {
            assertThat(alert.alertId()).isEqualTo("HIGH_RISK_FINDING:quality_finding:qf-critical");
            assertThat(alert.status()).isEqualTo(QualityDashboardAlertStatus.ACKNOWLEDGED);
        });
        assertThat(withTenant("tenant-A",
            () -> service.alerts(
                new QualityDashboardAlertFilter(null, now.plusSeconds(1), null,
                    QualityDashboardAlertStatus.OPEN, null),
                0, 20)).items()).isEmpty();
    }

    @Test
    void acknowledgeAlertIsTenantScopedAndRejectsMissingAlert() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedFinding("tenant-A", "qf-critical", QualityFindingSeverity.P1,
            QualityFindingStatus.ASSIGNED, "dept-a", now);
        withTenant("tenant-A", () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(1), null)));

        assertThatThrownBy(() -> withTenant("tenant-B",
            () -> service.acknowledgeAlert("HIGH_RISK_FINDING:quality_finding:qf-critical")))
            .hasMessageContaining("质控预警 不存在");
    }

    @Test
    void resolvedAlertReopensWhenSourceBecomesActiveAgain() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedFinding("tenant-A", "qf-critical", QualityFindingSeverity.P1,
            QualityFindingStatus.ASSIGNED, "dept-a", now);

        withTenant("tenant-A", () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(1), null)));
        jdbc.update("""
            UPDATE quality_finding
               SET status = 'CLOSED',
                   updated_at = ?
             WHERE tenant_id = 'tenant-A'
               AND finding_id = 'qf-critical'
            """, java.sql.Timestamp.from(now.plusSeconds(60)));
        withTenant("tenant-A", () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(120), null)));
        assertThat(alerts.findAll()).singleElement().satisfies(alert ->
            assertThat(alert.status()).isEqualTo(QualityDashboardAlertStatus.RESOLVED));

        jdbc.update("""
            UPDATE quality_finding
               SET status = 'ASSIGNED',
                   updated_at = ?
             WHERE tenant_id = 'tenant-A'
               AND finding_id = 'qf-critical'
            """, java.sql.Timestamp.from(now.plusSeconds(180)));
        QualityDashboardResponse response = withTenant("tenant-A",
            () -> service.dashboard(new QualityDashboardFilter(null, now.plusSeconds(240), null)));

        assertThat(response.summary().activeAlerts()).isEqualTo(1);
        assertThat(alerts.findAll()).singleElement().satisfies(alert ->
            assertThat(alert.status()).isEqualTo(QualityDashboardAlertStatus.OPEN));
    }

    private void seedFinding(
            String tenantId,
            String findingId,
            QualityFindingSeverity severity,
            QualityFindingStatus status,
            String departmentId,
            Instant createdAt) {
        findings.save(new QualityFinding(
            null, findingId, tenantId, "run-" + findingId, "result-" + findingId,
            "indicator-" + departmentId, "FIND." + findingId,
            "质控问题 " + findingId, "质控命中需复核",
            severity, status, "病历证据 evidence-" + findingId, departmentId,
            createdAt.plusSeconds(86400), createdAt, "qa-1", createdAt, "qa-1", "trace-quality"));
    }

    private void seedTask(
            String tenantId,
            String taskId,
            String findingId,
            RectificationTaskStatus status,
            String departmentId,
            Instant dueAt,
            Instant createdAt) {
        tasks.save(new RectificationTask(
            null, taskId, tenantId, findingId, departmentId, "head-1", status,
            dueAt, "整改任务说明 " + taskId, "rect-evidence-" + taskId,
            status == RectificationTaskStatus.CLOSED ? createdAt.plusSeconds(3600) : null,
            status == RectificationTaskStatus.CLOSED ? "head-1" : null,
            status == RectificationTaskStatus.CLOSED ? createdAt.plusSeconds(7200) : null,
            createdAt, "qa-1", createdAt, "qa-1", "trace-quality"));
    }

    private void seedRecommendation(
            String tenantId, String cardId, RecommendationCardStatus status, Instant createdAt) {
        String triggerId = "rt-" + UUID.randomUUID();
        triggers.save(new RecommendationTrigger(
            null, triggerId, tenantId, "TRG." + triggerId, "order-sign",
            "event-1", "snapshot-1", "patient-1", "enc-1", "pathway-1",
            "WARD_ORDER", "runtime-release-test", "sha256:trigger", RecommendationTriggerStatus.EVALUATED,
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

    private static <T> T withTenant(String tenantId, ThrowingSupplier<T> supplier) {
        try {
            return RequestContext.callWith(
                new RequestContext.Snapshot("trace-quality", OrgScope.tenant(tenantId), "qa-1"),
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
