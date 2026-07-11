package com.medkernel.engine.quality.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.medkernel.engine.evaluation.EvaluationEngineService;
import com.medkernel.engine.evaluation.EvaluationModelStatus;
import com.medkernel.engine.evaluation.EvaluationResultLevel;
import com.medkernel.engine.evaluation.EvaluationRunRequest;
import com.medkernel.engine.evaluation.EvaluationRunResponse;
import com.medkernel.engine.evaluation.EvaluationRunStatus;
import com.medkernel.engine.evaluation.EvaluationResultRequest;
import com.medkernel.engine.evaluation.ManualQualityRectificationBridge;
import com.medkernel.engine.evaluation.QualityFindingSeverity;
import com.medkernel.engine.release.ReleaseManifestHash;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
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
@Import({ InsuranceQualityService.class, ManualQualityRectificationBridge.class })
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
        jdbc.update("DELETE FROM rectification_task");
        jdbc.update("DELETE FROM quality_finding");
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
            new QualityCaseReviewRequest("snapshot-case", "A9", "dept-records")));

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
        AtomicReference<String> insertedFindingId = mockEvaluationRunWithPersistedFinding("tenant-A", "run-ins-1", now);

        InsuranceAuditResponse response = withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-ins",
                "A9",
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
        assertThat(jdbc.queryForMap("""
            SELECT issue.evaluation_run_id, issue.finding_id, finding.run_id, finding.indicator_id, finding.finding_code
              FROM mk_quality_insurance_issue issue
              JOIN quality_finding finding
                ON finding.tenant_id = issue.tenant_id
               AND finding.finding_id = issue.finding_id
             WHERE issue.tenant_id = 'tenant-A'
               AND issue.issue_id = ?
            """, response.issues().get(0).issueId()))
            .containsEntry("EVALUATION_RUN_ID", "run-ins-1")
            .containsEntry("FINDING_ID", insertedFindingId.get())
            .containsEntry("RUN_ID", "run-ins-1")
            .containsEntry("INDICATOR_ID", "indicator-insurance")
            .containsEntry("FINDING_CODE", "INSURANCE." + response.issues().get(0).issueId());

        ArgumentCaptor<EvaluationRunRequest> run = ArgumentCaptor.forClass(EvaluationRunRequest.class);
        verify(evaluations).run(run.capture());
        assertThat(run.getValue().runCode()).startsWith("INSURANCE-AUDIT-");
        assertThat(run.getValue().runtimeReleaseId()).isEqualTo("runtime-release-quality");
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
    void insuranceAuditManualRuleCreatesDirectRectificationWithoutActiveIndicator() {
        Instant now = Instant.parse("2026-06-05T02:15:00Z");
        seedSnapshot("tenant-A", "snapshot-manual-ins", "patient-manual-ins", "enc-manual-ins", now);
        seedClaim("tenant-A", "claim-manual-ins", "patient-manual-ins", "enc-manual-ins",
            new BigDecimal("1500.00"), now);

        InsuranceAuditResponse response = withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-manual-ins",
                "A9",
                "INSURANCE_RULE_MANUAL",
                "dept-insurance",
                now.plusSeconds(604800),
                List.of(new InsuranceAuditRuleRequest(
                    "RULE-FEE-MANUAL",
                    "2026-A",
                    InsuranceIssueType.FEE,
                    QualityFindingSeverity.P1,
                    new BigDecimal("1000.00"),
                    null,
                    null,
                    "费用超过本次医保规则阈值")))));

        assertThat(response.auditStatus()).isEqualTo(InsuranceAuditStatus.ISSUE_FOUND);
        assertThat(response.evaluationRunId()).isNull();
        assertThat(response.findingCount()).isEqualTo(1);
        assertThat(response.taskCount()).isEqualTo(1);
        assertThat(response.issues()).singleElement().satisfies(issue ->
            assertThat(issue.status()).isEqualTo(InsuranceIssueStatus.RECTIFICATION_CREATED));
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM quality_finding
            WHERE tenant_id = 'tenant-A'
              AND indicator_id = 'INSURANCE_RULE_MANUAL'
              AND evidence_summary LIKE '%未绑定生效评价指标%'
              AND evidence_summary NOT LIKE '%未绑定生效质控指标%'
            """, Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM rectification_task t
            JOIN quality_finding f
              ON f.tenant_id = t.tenant_id
             AND f.finding_id = t.finding_id
            WHERE t.tenant_id = 'tenant-A'
              AND f.indicator_id = 'INSURANCE_RULE_MANUAL'
            """, Long.class)).isEqualTo(1L);
        verify(evaluations, never()).run(any());
    }

    @Test
    void insuranceAuditUpdatesOnlyIssuesCreatedByCurrentAuditRun() {
        Instant now = Instant.parse("2026-06-05T02:30:00Z");
        seedSnapshot("tenant-A", "snapshot-scope", "patient-scope", "enc-scope", now);
        seedClaim("tenant-A", "claim-scope", "patient-scope", "enc-scope", new BigDecimal("1300.00"), now);
        seedOpenInsuranceIssue("tenant-A", "ins-stale", "snapshot-scope", "claim-stale", now);
        mockEvaluationRunWithPersistedFinding("tenant-A", "run-scope-1", now);

        InsuranceAuditResponse response = withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-scope",
                "A9",
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
    void insuranceAuditFailsWhenEvaluationRunDoesNotPersistMatchingFinding() {
        Instant now = Instant.parse("2026-06-05T02:45:00Z");
        seedSnapshot("tenant-A", "snapshot-missing-finding", "patient-missing-finding", "enc-missing-finding", now);
        seedClaim("tenant-A", "claim-missing-finding", "patient-missing-finding", "enc-missing-finding",
            new BigDecimal("1300.00"), now);
        when(evaluations.run(any())).thenReturn(new EvaluationRunResponse(
            "run-missing-finding", EvaluationRunStatus.RECORDED, 1, 0, 0, "trace-missing-finding"));

        assertThatThrownBy(() -> withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-missing-finding",
                "A9",
                "indicator-insurance",
                "dept-insurance",
                now.plusSeconds(604800),
                List.of(new InsuranceAuditRuleRequest(
                    "RULE-FEE-MISSING",
                    "2026-A",
                    InsuranceIssueType.FEE,
                    QualityFindingSeverity.P1,
                    new BigDecimal("1000.00"),
                    null,
                    null,
                    "费用超过版本化规则阈值但评估未返回质量问题"))))))
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseInstanceOf(ApiException.class)
            .hasStackTraceContaining("医保审核评估运行未生成对应质量问题");
    }

    @Test
    void insuranceAuditRejectsClaimIndicatorOutsideSnapshotRuntimeRelease() {
        Instant now = Instant.parse("2026-06-05T02:50:00Z");
        seedSnapshotWithoutEvaluationAsset(
            "tenant-A", "snapshot-runtime-missing", "patient-runtime-missing", "enc-runtime-missing", now);
        seedClaim("tenant-A", "claim-runtime-missing", "patient-runtime-missing", "enc-runtime-missing",
            new BigDecimal("1300.00"), now);
        mockEvaluationRunWithPersistedFinding("tenant-A", "run-runtime-missing", now);

        assertThatThrownBy(() -> withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-runtime-missing",
                "A9",
                "indicator-insurance",
                "dept-insurance",
                now.plusSeconds(604800),
                List.of(new InsuranceAuditRuleRequest(
                    "RULE-FEE-RUNTIME",
                    "2026-A",
                    InsuranceIssueType.FEE,
                    QualityFindingSeverity.P1,
                    new BigDecimal("1000.00"),
                    null,
                    null,
                    "费用超过版本化规则阈值但指标未进入机构生效版本"))))))
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseInstanceOf(ApiException.class)
            .hasStackTraceContaining("医保审核评价指标未进入当前机构生效版本");
        verify(evaluations, never()).run(any());
    }

    @Test
    void insuranceAuditRejectsNonClaimIndicatorEvenWhenActiveInRuntimeRelease() {
        Instant now = Instant.parse("2026-06-05T02:55:00Z");
        seedSnapshot("tenant-A", "snapshot-non-claim", "patient-non-claim", "enc-non-claim", now);
        jdbc.update("""
            UPDATE evaluation_indicator
               SET subject_type = 'MEDICAL_RECORD'
             WHERE indicator_id = 'indicator-insurance'
            """);
        seedClaim("tenant-A", "claim-non-claim", "patient-non-claim", "enc-non-claim",
            new BigDecimal("1300.00"), now);
        mockEvaluationRunWithPersistedFinding("tenant-A", "run-non-claim", now);

        assertThatThrownBy(() -> withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-non-claim",
                "A9",
                "indicator-insurance",
                "dept-insurance",
                now.plusSeconds(604800),
                List.of(new InsuranceAuditRuleRequest(
                    "RULE-FEE-NON-CLAIM",
                    "2026-A",
                    InsuranceIssueType.FEE,
                    QualityFindingSeverity.P1,
                    new BigDecimal("1000.00"),
                    null,
                    null,
                    "费用超过版本化规则阈值但指标主体不是医保结算"))))))
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseInstanceOf(ApiException.class)
            .hasStackTraceContaining("医保审核评价指标必须面向医保合规主体");
        verify(evaluations, never()).run(any());
    }

    @Test
    void insuranceAuditWithoutClaimReturnsInsufficientDataAndDoesNotCreateIssue() {
        Instant now = Instant.parse("2026-06-05T03:00:00Z");
        seedSnapshot("tenant-A", "snapshot-empty", "patient-empty", "enc-empty", now);

        InsuranceAuditResponse response = withTenant("tenant-A", () -> service.insuranceAudit(
            new InsuranceAuditRequest(
                "snapshot-empty", "A9", "indicator-insurance", "dept-insurance",
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

    @Test
    void listInsuranceIssuesUsesTenantScopeFiltersAndServerPaging() {
        Instant now = Instant.parse("2026-06-05T04:00:00Z");
        seedOpenInsuranceIssue("tenant-A", "ins-open-p2", "snapshot-list", "claim-open", now);
        seedInsuranceIssue("tenant-A", "ins-closed-p1", "snapshot-list", "claim-closed",
            InsuranceIssueStatus.RECTIFICATION_CREATED, QualityFindingSeverity.P1, now.plusSeconds(1));
        seedOpenInsuranceIssue("tenant-B", "ins-cross-tenant", "snapshot-list", "claim-cross", now.plusSeconds(2));

        PageResponse<InsuranceIssuePageItemResponse> page = withTenant("tenant-A", () -> service.listInsuranceIssues(
            new InsuranceIssueFilter(InsuranceIssueStatus.OPEN, QualityFindingSeverity.P2, null),
            new PageRequest(1, 20, null)));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(issue -> {
            assertThat(issue.issueId()).isEqualTo("ins-open-p2");
            assertThat(issue.claimId()).isEqualTo("claim-open");
            assertThat(issue.status()).isEqualTo(InsuranceIssueStatus.OPEN);
            assertThat(issue.severity()).isEqualTo(QualityFindingSeverity.P2);
            assertThat(issue.departmentId()).isEqualTo("dept-insurance");
            assertThat(issue.evidenceSummary()).contains("既有开放问题");
        });
    }

    private void seedSnapshot(String tenantId, String snapshotId, String patientId, String encounterId, Instant createdAt) {
        seedSnapshot(tenantId, snapshotId, patientId, encounterId, createdAt, true);
    }

    private void seedSnapshotWithoutEvaluationAsset(
            String tenantId, String snapshotId, String patientId, String encounterId, Instant createdAt) {
        seedSnapshot(tenantId, snapshotId, patientId, encounterId, createdAt, false);
    }

    private void seedSnapshot(
            String tenantId,
            String snapshotId,
            String patientId,
            String encounterId,
            Instant createdAt,
            boolean includeEvaluationAsset) {
        seedPlatformBaseline(createdAt);
        seedEvaluationIndicator(tenantId, createdAt);
        seedRuntimeRelease(tenantId, createdAt, includeEvaluationAsset);
        jdbc.update("""
            INSERT INTO context_snapshot (
                snapshot_id, tenant_id, org_unit_id, patient_id, encounter_id,
                runtime_release_id,
                status, missing_fields, mapping_status, extensions_json, quality_status,
                trace_id, signature, created_at, created_by, request_id, org_path
            ) VALUES (?, ?, 'dept-records', ?, ?,
                'runtime-release-quality',
                'ACTIVE', '[]', '{}', '{}', 'VALID',
                'trace-quality', 'sig', ?, 'tester', ?, '/platform/group/hospital/dept-records')
            """, snapshotId, tenantId, patientId, encounterId, java.sql.Timestamp.from(createdAt),
            "req-" + snapshotId);
    }

    private void seedRuntimeRelease(String tenantId, Instant createdAt, boolean includeEvaluationAsset) {
        jdbc.update("DELETE FROM clinical_runtime_release_item WHERE release_id = 'runtime-release-quality'");
        String contentHash = "1111111111111111111111111111111111111111111111111111111111111111";
        String manifestHash = includeEvaluationAsset
            ? ReleaseManifestHash.sha256(List.of(String.join(
                "\u001f",
                tenantId,
                "HOSPITAL",
                "EVALUATION",
                "INS.CLAIM.RUNTIME",
                "ACTIVE",
                "version-indicator-insurance",
                "V1",
                contentHash)))
            : ReleaseManifestHash.sha256(List.of());
        jdbc.update("""
            MERGE INTO clinical_runtime_release (
                release_id, tenant_id, hospital_id, revision_no, platform_baseline_release_id,
                manifest_sha256, activated_at, activated_by, created_at, created_by, trace_id
            ) KEY(release_id) VALUES (
                'runtime-release-quality', ?, 'hospital-quality', 1, 'platform-baseline-quality',
                ?,
                ?, 'tester', ?, 'tester', 'trace-quality'
            )
            """, tenantId, manifestHash, java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        if (includeEvaluationAsset) {
            jdbc.update("""
                INSERT INTO clinical_runtime_release_item (
                    release_id, source_tenant_id, source_layer, asset_type, asset_identity,
                    entry_state, version_id, version_no, content_hash, created_at, created_by, trace_id
                ) VALUES (
                    'runtime-release-quality', ?, 'HOSPITAL', 'EVALUATION', 'INS.CLAIM.RUNTIME',
                    'ACTIVE', 'version-indicator-insurance', 'V1', ?, ?, 'tester', 'trace-quality'
                )
                """, tenantId, contentHash, java.sql.Timestamp.from(createdAt));
        }
    }

    private void seedEvaluationIndicator(String tenantId, Instant createdAt) {
        jdbc.update("""
            MERGE INTO asset_identity (
                tenant_id, asset_type, asset_identity, status, latest_version_sequence,
                created_at, created_by, updated_at, updated_by, trace_id
            ) KEY(tenant_id, asset_type, asset_identity) VALUES (
                ?, 'EVALUATION', 'INS.CLAIM.RUNTIME', 'ACTIVE', 1,
                ?, 'tester', ?, 'tester', 'trace-quality'
            )
            """, tenantId, java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        jdbc.update("""
            MERGE INTO mk_version_asset_version (
                version_id, tenant_id, asset_type, asset_identity, version_no,
                org_path, applicable_scope, content_hash, status, active_scope_key,
                source_ref, effective_from, effective_to,
                created_at, created_by, updated_at, updated_by, trace_id,
                safety_policy, override_policy
            ) KEY(version_id) VALUES (
                'version-indicator-insurance', ?, 'EVALUATION', 'INS.CLAIM.RUNTIME', 'V1',
                '/platform/group/hospital', 'CLAIM', '1111111111111111111111111111111111111111111111111111111111111111',
                'PUBLISHED', 'version:version-indicator-insurance',
                '医保审核演练', ?, NULL,
                ?, 'tester', ?, 'tester', 'trace-quality',
                'NORMAL', 'FREE'
            )
            """, tenantId,
            java.sql.Timestamp.from(createdAt),
            java.sql.Timestamp.from(createdAt),
            java.sql.Timestamp.from(createdAt));
        jdbc.update("""
            MERGE INTO evaluation_indicator (
                indicator_id, tenant_id, indicator_code, version_no, name, subject_type,
                denominator_definition, numerator_definition, exclusion_definition, scoring_definition,
                time_window, organization_scope, responsible_department_id, source_ref, status,
                published_at, published_by, activated_at, created_at, created_by, updated_at, updated_by, trace_id
            ) KEY(indicator_id) VALUES (
                'indicator-insurance', ?, 'INS.CLAIM.RUNTIME', 1, '医保合规运行指标', 'CLAIM',
                '{}', '{}', NULL, 'P1',
                'DISCHARGE+24H', '全院', 'dept-insurance', '医保审核演练', 'ACTIVE',
                ?, 'tester', ?, ?, 'tester', ?, 'tester', 'trace-quality'
            )
            """, tenantId,
            java.sql.Timestamp.from(createdAt),
            java.sql.Timestamp.from(createdAt),
            java.sql.Timestamp.from(createdAt),
            java.sql.Timestamp.from(createdAt));
    }

    private void seedPlatformBaseline(Instant createdAt) {
        jdbc.update("""
            MERGE INTO platform_baseline_release (
                baseline_release_id, revision_no, manifest_sha256,
                published_at, published_by, created_at, created_by, trace_id
            ) KEY(baseline_release_id) VALUES (
                'platform-baseline-quality', 1,
                '0000000000000000000000000000000000000000000000000000000000000000',
                ?, 'tester', ?, 'tester', 'trace-quality'
            )
            """, java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
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
        seedInsuranceIssue(tenantId, issueId, snapshotId, claimId,
            InsuranceIssueStatus.OPEN, QualityFindingSeverity.P2, createdAt);
    }

    private void seedInsuranceIssue(
            String tenantId, String issueId, String snapshotId, String claimId,
            InsuranceIssueStatus status, QualityFindingSeverity severity, Instant createdAt) {
        jdbc.update("""
            INSERT INTO mk_quality_insurance_issue (
                issue_id, tenant_id, context_snapshot_id, claim_id, patient_id, encounter_id,
                department_id, issue_type, severity, status, rule_code, rule_version,
                claim_amount, threshold_amount, evidence_summary,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, 'patient-scope', 'enc-scope', 'dept-insurance',
                'FEE', ?, ?, 'RULE-STALE', '2026-A',
                88.00, 100.00, '既有开放问题',
                ?, 'tester', ?, 'tester', ?)
            """, issueId, tenantId, snapshotId, claimId, severity.name(), status.name(),
            java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt), "trace-" + issueId);
    }

    private String insertEvaluationFindingFromRequest(
            String tenantId, String runId, EvaluationRunRequest request, Instant createdAt) {
        EvaluationResultRequest result = request.results().get(0);
        String findingId = "qf-test-" + result.subjectRefId();
        String resultId = "ers-test-" + result.subjectRefId();
        jdbc.update("""
            INSERT INTO quality_finding (
                finding_id, tenant_id, run_id, result_id, indicator_id, finding_code,
                title, description, severity, status, evidence_summary,
                responsible_department_id, due_at,
                created_at, created_by, updated_at, updated_by, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?,
                ?, ?, ?, 'ASSIGNED', ?,
                ?, ?,
                ?, 'tester', ?, 'tester', 'trace-ins')
            """,
            findingId, tenantId, runId, resultId, result.indicatorId(),
            result.findings().get(0).findingCode(),
            result.findings().get(0).title(),
            result.findings().get(0).description(),
            result.findings().get(0).severity().name(),
            result.findings().get(0).evidenceSummary(),
            result.findings().get(0).responsibleDepartmentId(),
            java.sql.Timestamp.from(result.findings().get(0).dueAt()),
            java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        return findingId;
    }

    private AtomicReference<String> mockEvaluationRunWithPersistedFinding(
            String tenantId, String runId, Instant createdAt) {
        AtomicReference<String> insertedFindingId = new AtomicReference<>();
        when(evaluations.run(any())).thenAnswer(invocation -> {
            EvaluationRunRequest request = invocation.getArgument(0);
            insertedFindingId.set(insertEvaluationFindingFromRequest(tenantId, runId, request, createdAt));
            return new EvaluationRunResponse(
                runId, EvaluationRunStatus.RECORDED, request.results().size(), 1, 1, "trace-ins");
        });
        return insertedFindingId;
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
