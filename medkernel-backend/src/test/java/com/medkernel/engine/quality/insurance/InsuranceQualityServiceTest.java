package com.medkernel.engine.quality.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medkernel.engine.evaluation.EvaluationEngineService;
import com.medkernel.engine.evaluation.EvaluationModelStatus;
import com.medkernel.engine.evaluation.EvaluationResultLevel;
import com.medkernel.engine.evaluation.EvaluationRunRequest;
import com.medkernel.engine.evaluation.EvaluationRunResponse;
import com.medkernel.engine.evaluation.EvaluationRunStatus;
import com.medkernel.engine.evaluation.QualityFindingSeverity;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@Import(InsuranceQualityService.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:insurance-quality-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class InsuranceQualityServiceTest {

    @Autowired InsuranceQualityService service;
    @Autowired JdbcTemplate jdbc;

    @MockBean EvaluationEngineService evaluations;

    @AfterEach
    void clear() {
        RequestContext.clear();
        jdbc.update("DELETE FROM mk_quality_insurance_issue");
        jdbc.update("DELETE FROM mk_quality_drg_grouping");
        jdbc.update("DELETE FROM mk_quality_case_review");
        jdbc.update("DELETE FROM mk_clinical_claim");
    }

    @Test
    void caseReviewPersistsEvaluationRunEvidenceWithoutInventingFindings() {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        seedSnapshot("tenant-A", "snapshot-case", "patient-case", "enc-case", now);
        when(evaluations.evaluateSnapshot(any())).thenReturn(new EvaluationRunResponse(
            "run-case-1", EvaluationRunStatus.RECORDED, 2, 1, 1,
            EvaluationModelStatus.MODEL_DISABLED, "MODEL_DISABLED_DETERMINISTIC_RULES", "trace-case"));

        QualityCaseReviewResponse response = withTenant("tenant-A", () -> service.caseReview(
            new QualityCaseReviewRequest("snapshot-case", "A9", "pkg-quality-v1", "dept-records")));

        assertThat(response.reviewStatus()).isEqualTo(CaseReviewStatus.NON_COMPLIANT);
        assertThat(response.evaluationRunId()).isEqualTo("run-case-1");
        assertThat(response.findingCount()).isEqualTo(1);
        assertThat(response.taskCount()).isEqualTo(1);
        assertThat(response.modelStatus()).isEqualTo(EvaluationModelStatus.MODEL_DISABLED);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM mk_quality_case_review WHERE tenant_id = 'tenant-A' AND context_snapshot_id = 'snapshot-case'",
            Long.class)).isEqualTo(1L);
    }

    @Test
    void drgGroupingPersistsVersionedMismatchExplanationPerSnapshot() {
        Instant now = Instant.parse("2026-06-05T01:00:00Z");
        seedSnapshot("tenant-A", "snapshot-drg", "patient-drg", "enc-drg", now);

        DrgGroupingResponse response = withTenant("tenant-A", () -> service.drgGrouping(
            new DrgGroupingRequest("snapshot-drg", "DRG-GROUPER-2026A", "GROUP-A", "GROUP-B",
                "dept-records", "首页诊断与费用组合进入复核")));

        assertThat(response.groupingStatus()).isEqualTo(DrgGroupingStatus.MISMATCHED);
        assertThat(response.explanation()).contains("DRG-GROUPER-2026A", "GROUP-A", "GROUP-B");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM mk_quality_drg_grouping WHERE tenant_id = 'tenant-A' AND context_snapshot_id = 'snapshot-drg'",
            Long.class)).isEqualTo(1L);
    }

    @Test
    void insuranceAuditUsesRealClaimAndCreatesEvaluationFindingForRectification() {
        Instant now = Instant.parse("2026-06-05T02:00:00Z");
        seedSnapshot("tenant-A", "snapshot-ins", "patient-ins", "enc-ins", now);
        seedClaim("tenant-A", "claim-ins", "patient-ins", "enc-ins", new BigDecimal("1200.00"), now);
        seedClaim("tenant-B", "claim-other", "patient-ins", "enc-ins", new BigDecimal("9900.00"), now);
        when(evaluations.run(any())).thenReturn(new EvaluationRunResponse(
            "run-ins-1", EvaluationRunStatus.RECORDED, 1, 1, 1, "trace-ins"));

        InsuranceAuditResponse response = withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-ins",
                "A9",
                "pkg-quality-v1",
                "indicator-insurance",
                "dept-insurance",
                now.plusSeconds(604800),
                List.of(new InsuranceAuditRuleRequest(
                    "RULE-FEE-A",
                    "2026-A",
                    InsuranceIssueType.FEE,
                    QualityFindingSeverity.P1,
                    new BigDecimal("1000.00"),
                    null,
                    null,
                    "费用超过版本化规则阈值")))));

        assertThat(response.auditStatus()).isEqualTo(InsuranceAuditStatus.ISSUE_FOUND);
        assertThat(response.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.claimId()).isEqualTo("claim-ins");
            assertThat(issue.issueType()).isEqualTo(InsuranceIssueType.FEE);
            assertThat(issue.severity()).isEqualTo(QualityFindingSeverity.P1);
            assertThat(issue.evidenceSummary()).contains("claim-ins", "RULE-FEE-A", "1200.00", "1000.00");
        });
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM mk_quality_insurance_issue WHERE tenant_id = 'tenant-A'",
            Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
            SELECT department_id FROM mk_quality_insurance_issue
            WHERE tenant_id = 'tenant-A' AND issue_id = ?
            """, String.class, response.issues().get(0).issueId())).isEqualTo("dept-insurance");

        ArgumentCaptor<EvaluationRunRequest> run = ArgumentCaptor.forClass(EvaluationRunRequest.class);
        verify(evaluations).run(run.capture());
        assertThat(run.getValue().runCode()).startsWith("INSURANCE-AUDIT-");
        assertThat(run.getValue().results()).singleElement().satisfies(result -> {
            assertThat(result.indicatorId()).isEqualTo("indicator-insurance");
            assertThat(result.subjectRefId()).isEqualTo("claim-ins");
            assertThat(result.resultLevel()).isEqualTo(EvaluationResultLevel.NON_COMPLIANT);
            assertThat(result.findings()).singleElement().satisfies(finding -> {
                assertThat(finding.responsibleDepartmentId()).isEqualTo("dept-insurance");
                assertThat(finding.dueAt()).isEqualTo(now.plusSeconds(604800));
            });
        });
    }

    @Test
    void insuranceAuditUpdatesOnlyIssuesCreatedByCurrentAuditRun() {
        Instant now = Instant.parse("2026-06-05T02:30:00Z");
        seedSnapshot("tenant-A", "snapshot-scope", "patient-scope", "enc-scope", now);
        seedClaim("tenant-A", "claim-scope", "patient-scope", "enc-scope", new BigDecimal("1300.00"), now);
        seedOpenInsuranceIssue("tenant-A", "ins-stale", "snapshot-scope", "claim-stale", now);
        when(evaluations.run(any())).thenReturn(new EvaluationRunResponse(
            "run-scope-1", EvaluationRunStatus.RECORDED, 1, 1, 1, "trace-scope"));

        InsuranceAuditResponse response = withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-scope",
                "A9",
                "pkg-quality-v1",
                "indicator-insurance",
                "dept-insurance",
                now.plusSeconds(604800),
                List.of(new InsuranceAuditRuleRequest(
                    "RULE-FEE-SCOPE",
                    "2026-A",
                    InsuranceIssueType.FEE,
                    QualityFindingSeverity.P2,
                    new BigDecimal("1000.00"),
                    null,
                    null,
                    "费用超过版本化规则阈值")))));

        assertThat(response.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.issueId()).isNotEqualTo("ins-stale");
            assertThat(issue.status()).isEqualTo(InsuranceIssueStatus.RECTIFICATION_CREATED);
        });
        assertThat(jdbc.queryForObject("""
            SELECT status FROM mk_quality_insurance_issue
            WHERE tenant_id = 'tenant-A' AND issue_id = 'ins-stale'
            """, String.class)).isEqualTo(InsuranceIssueStatus.OPEN.name());
    }

    @Test
    void insuranceAuditWithoutClaimReturnsInsufficientDataAndDoesNotCreateIssue() {
        Instant now = Instant.parse("2026-06-05T03:00:00Z");
        seedSnapshot("tenant-A", "snapshot-empty", "patient-empty", "enc-empty", now);

        InsuranceAuditResponse response = withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-empty", "A9", "pkg-quality-v1", "indicator-insurance", "dept-insurance",
                now.plusSeconds(604800),
                List.of(new InsuranceAuditRuleRequest(
                    "RULE-FEE-A", "2026-A", InsuranceIssueType.FEE, QualityFindingSeverity.P1,
                    new BigDecimal("1000.00"), null, null, "费用超过版本化规则阈值")))));

        assertThat(response.auditStatus()).isEqualTo(InsuranceAuditStatus.INSUFFICIENT_DATA);
        assertThat(response.issues()).isEmpty();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM mk_quality_insurance_issue WHERE tenant_id = 'tenant-A'",
            Long.class)).isZero();
        verify(evaluations, never()).run(any());
    }

    private void seedSnapshot(String tenantId, String snapshotId, String patientId, String encounterId, Instant createdAt) {
        jdbc.update("""
            INSERT INTO context_snapshot (
                snapshot_id, tenant_id, org_unit_id, request_id, org_path, package_version,
                patient_id, encounter_id, knowledge_pkg_version, rule_pkg_version, pathway_pkg_version,
                status, quality_status, trace_id, signature, created_at, created_by
            ) VALUES (?, ?, 'dept-records', ?, '/platform/group/hospital/dept-records', 'pkg-quality-v1',
                ?, ?, 'know-v1', 'rule-v1', 'path-v1', 'ACTIVE', 'VALID', 'trace-quality', 'sig', ?, 'tester')
            """, snapshotId, tenantId, "req-" + snapshotId, patientId, encounterId, java.sql.Timestamp.from(createdAt));
    }

    private void seedClaim(
            String tenantId, String claimId, String patientId, String encounterId,
            BigDecimal amount, Instant createdAt) {
        jdbc.update("""
            INSERT INTO mk_clinical_claim (
                claim_id, tenant_id, org_path, source_system, source_id, fhir_resource_id,
                patient_id, encounter_id, claim_type, status, total_amount,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, '/platform/group/hospital/dept-records', 'INSURANCE', ?, ?, ?, ?, 'DRG',
                'SUBMITTED', ?, ?, 'tester', ?, 'tester', ?)
            """, claimId, tenantId, "SRC-" + claimId, "Claim/" + claimId, patientId, encounterId, amount,
            java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt), "trace-" + claimId);
    }

    private void seedOpenInsuranceIssue(
            String tenantId, String issueId, String snapshotId, String claimId, Instant createdAt) {
        jdbc.update("""
            INSERT INTO mk_quality_insurance_issue (
                issue_id, tenant_id, context_snapshot_id, claim_id, patient_id, encounter_id,
                department_id, issue_type, severity, status, rule_code, rule_version,
                claim_amount, threshold_amount, evidence_summary,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, 'patient-scope', 'enc-scope', 'dept-insurance',
                'FEE', 'P2', 'OPEN', 'RULE-STALE', '2026-A',
                88.00, 100.00, '既有开放问题',
                ?, 'tester', ?, 'tester', ?)
            """, issueId, tenantId, snapshotId, claimId,
            java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt), "trace-" + issueId);
    }

    private <T> T withTenant(String tenantId, java.util.concurrent.Callable<T> action) {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-quality", OrgScope.tenant(tenantId), "qa-1"));
        try {
            return action.call();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
