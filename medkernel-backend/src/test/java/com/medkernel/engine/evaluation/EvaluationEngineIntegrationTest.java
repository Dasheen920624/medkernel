package com.medkernel.engine.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.org.OrgAssignmentValidator;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetDependencyService;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.VersionReleaseService;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@Import({
    EvaluationEngineService.class,
    EvaluationVersionedAssetAdapter.class,
    AssetDependencyService.class,
    AssetVersionService.class,
    VersionReleaseService.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:evaluation-flow-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class EvaluationEngineIntegrationTest {

    @Autowired EvaluationEngineService service;
    @Autowired EvaluationIndicatorRepository indicators;
    @Autowired EvaluationRunRepository runs;
    @Autowired EvaluationResultRepository results;
    @Autowired QualityFindingRepository findings;
    @Autowired RectificationTaskRepository tasks;
    @Autowired RectificationReviewRepository reviews;
    @Autowired EvaluationIdempotencyKeyRepository idempotencyKeys;

    @MockBean AuditRecorder auditRecorder;
    @MockBean StateTransitionRecorder transitions;
    @MockBean DiagnoseResponseAssembler diagnoseAssembler;
    @MockBean com.medkernel.engine.context.CanonicalResourceRepository canonicalResources;
    @MockBean com.medkernel.engine.context.ContextSnapshotRepository snapshots;
    @MockBean com.medkernel.engine.rule.RuleDslEvaluator ruleEvaluator;
    @MockBean OrgAssignmentValidator orgAssignmentValidator;
    @MockBean PermissionEvaluator permissionEvaluator;

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-real-flow", OrgScope.tenant("tenant-A"), "qa-1"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "qa-1",
                "n/a",
                List.of(new SimpleGrantedAuthority(RoleCode.HOSPITAL_ADMIN.authority()))
            )
        );
        when(permissionEvaluator.has(PermissionCode.TENANT_OVERRIDE)).thenReturn(true);
    }

    @AfterEach
    void wipe() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
        idempotencyKeys.deleteAll();
        reviews.deleteAll();
        tasks.deleteAll();
        findings.deleteAll();
        results.deleteAll();
        runs.deleteAll();
        indicators.deleteAll();
    }

    @Test
    void persistsIdempotentIndicatorRunRectificationAndReviewWorkflow() {
        EvaluationIndicator indicator = service.createIndicator(new EvaluationIndicatorCreateRequest(
            "IND.VTE.PROPHYLAXIS", 1, "静脉血栓预防完成率", EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            null, null,
            "DISCHARGE+24H", "全院住院科室", "dept-1", "guideline-1", "1.0.0"));
        service.submitIndicator(indicator.indicatorId());
        service.publishIndicator(
            indicator.indicatorId(),
            new EvaluationIndicatorReleaseRequest("集成测试审核通过")
        );
        service.grayIndicator(
            indicator.indicatorId(),
            new EvaluationIndicatorReleaseRequest("集成测试默认灰度")
        );
        service.activateIndicator(
            indicator.indicatorId(),
            new EvaluationIndicatorReleaseRequest("集成测试全量激活")
        );

        EvaluationRunResponse run = service.run(new EvaluationRunRequest(
            "RUN.VTE", EvaluationRunType.UPSTREAM_RESULT, "event-1", "snapshot-1",
            "patient-1", "enc-1", "DISCHARGE", "1.0.0", "sha256:run", Instant.now(),
            List.of(new EvaluationResultRequest(
                indicator.indicatorId(), EvaluationSubjectType.MEDICAL_RECORD, "record-1",
                new BigDecimal("70.5000"), EvaluationResultLevel.NON_COMPLIANT, true,
                "指标未达标", "evidence-1", "dept-1",
                List.of(new QualityFindingRequest(
                    "FIND.VTE.001", "未完成静脉血栓风险评估", "出院前未记录风险评估",
                    QualityFindingSeverity.P1, "缺少风险评估记录", "dept-1",
                    Instant.now().plusSeconds(86400), "head-1"))))));

        QualityFinding finding = service.listFindings(
            new QualityFindingFilter(QualityFindingSeverity.P1, null, "dept-1"),
            PageRequest.defaults()).items().getFirst();
        assertThat(run.taskCount()).isEqualTo(1);
        assertThat(service.listIndicators(null, PageRequest.defaults()).items())
            .extracting(EvaluationIndicator::status)
            .containsExactly(EvaluationIndicatorStatus.ACTIVE);
        assertThat(service.listResults(null, PageRequest.defaults()).items())
            .extracting(EvaluationResult::resultLevel)
            .containsExactly(EvaluationResultLevel.NON_COMPLIANT);

        RectificationSubmitRequest rectification =
            new RectificationSubmitRequest("补录风险评估记录", "proof-1");
        RectificationResponse submitted = service.submitRectification(
            finding.findingId(), rectification, "idem-rectification-1");
        RectificationResponse submittedReplay = service.submitRectification(
            finding.findingId(), rectification, "idem-rectification-1");
        assertThat(submittedReplay).isEqualTo(submitted);
        assertThatThrownBy(() -> service.submitRectification(
                finding.findingId(), new RectificationSubmitRequest("更换整改内容", "proof-2"),
                "idem-rectification-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_008);

        RectificationReviewRequest review =
            new RectificationReviewRequest(
                RectificationReviewDecision.APPROVED, "证据充分，允许闭环", "review-proof-1");
        RectificationReviewResponse approved = service.reviewRectification(
            finding.findingId(), review, "idem-review-1");
        RectificationReviewResponse approvedReplay = service.reviewRectification(
            finding.findingId(), review, "idem-review-1");
        assertThat(approvedReplay).isEqualTo(approved);
        assertThatThrownBy(() -> service.reviewRectification(
                finding.findingId(), new RectificationReviewRequest(
                    RectificationReviewDecision.WAIVED, "更换复核结论", null), "idem-review-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_008);

        QualityFindingDetailResponse detail = service.findingDetail(finding.findingId());
        assertThat(detail.finding().status()).isEqualTo(QualityFindingStatus.CLOSED);
        assertThat(detail.rectificationTask().status()).isEqualTo(RectificationTaskStatus.CLOSED);
        assertThat(detail.reviews())
            .extracting(RectificationReview::decision)
            .containsExactly(RectificationReviewDecision.APPROVED);
    }

    @Test
    void rectificationReportAggregatesPersistedTaskAndSafetyFacts() {
        Instant now = Instant.now();
        findings.save(finding(
            "qf-open-p0", QualityFindingSeverity.P0, QualityFindingStatus.ASSIGNED, "dept-1", now.minusSeconds(3600)));
        findings.save(finding(
            "qf-submitted-p2", QualityFindingSeverity.P2, QualityFindingStatus.REMEDIATING,
            "dept-1", now.plusSeconds(3600)));
        findings.save(finding(
            "qf-closed-p1", QualityFindingSeverity.P1, QualityFindingStatus.CLOSED, "dept-1", now));
        findings.save(finding(
            "qf-waived-p2", QualityFindingSeverity.P2, QualityFindingStatus.WAIVED, "dept-1", now));
        tasks.save(task(
            "task-open-p0", "qf-open-p0", RectificationTaskStatus.ASSIGNED, "dept-1", now.minusSeconds(3600)));
        tasks.save(task(
            "task-submitted-p2", "qf-submitted-p2", RectificationTaskStatus.SUBMITTED,
            "dept-1", now.plusSeconds(3600)));
        tasks.save(task(
            "task-closed-p1", "qf-closed-p1", RectificationTaskStatus.CLOSED, "dept-1", now));
        tasks.save(task(
            "task-waived-p2", "qf-waived-p2", RectificationTaskStatus.WAIVED, "dept-1", now));

        RectificationReportResponse report =
            service.rectificationReport(new RectificationReportFilter("dept-1"), now);

        assertThat(report.status()).isEqualTo(RectificationReportStatus.AVAILABLE);
        assertThat(report.totalTasks()).isEqualTo(4);
        assertThat(report.openTasks()).isEqualTo(2);
        assertThat(report.closedTasks()).isEqualTo(1);
        assertThat(report.waivedTasks()).isEqualTo(1);
        assertThat(report.overdueTasks()).isEqualTo(1);
        assertThat(report.highPriorityOpenTasks()).isEqualTo(1);
        assertThat(report.closureRate()).isEqualByComparingTo("0.2500");
        assertThat(report.sourceTable()).isEqualTo("rectification_task");
    }

    @Test
    void evaluateSnapshotReplaysExistingAutomaticRunThroughRealRepositories() {
        when(ruleEvaluator.evaluate(any(), any()))
            .thenReturn(ruleEvaluation(true, "分母规则定义校验", "patient.qualityReady", true))
            .thenReturn(ruleEvaluation(false, "分子规则定义校验", "patient.completed", false))
            .thenReturn(ruleEvaluation(true, "分母入组规则校验", "patient.qualityReady", true))
            .thenReturn(ruleEvaluation(false, "分子达标规则校验", "patient.completed", false));

        EvaluationIndicator indicator = service.createIndicator(new EvaluationIndicatorCreateRequest(
            "IND.AUTO.REPLAY", 1, "出院质量自动评估复现率", EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            null, "P1级严重质控缺陷",
            "DISCHARGE+24H", "全院住院科室", "dept-1", "guideline-1", "1.0.0"));
        service.submitIndicator(indicator.indicatorId());
        service.publishIndicator(
            indicator.indicatorId(),
            new EvaluationIndicatorReleaseRequest("集成测试审核通过")
        );
        service.grayIndicator(
            indicator.indicatorId(),
            new EvaluationIndicatorReleaseRequest("集成测试默认灰度")
        );
        service.activateIndicator(
            indicator.indicatorId(),
            new EvaluationIndicatorReleaseRequest("集成测试全量激活")
        );

        ContextSnapshot snapshot = new ContextSnapshot(
            null, "snap-auto-1", "tenant-A", "dept-1", null, null, "1.0.0",
            "patient-1", "enc-1", ContextSnapshotStatus.ACTIVE,
            "[]", "{}", QualityStatus.VALID, "trace-auto", "sig-auto", Instant.now(), "qa-1");
        CanonicalResource patient = new CanonicalResource(
            null, "res-auto-1", "snap-auto-1", "tenant-A", CanonicalResourceType.PATIENT,
            "{\"patientId\":\"patient-1\",\"qualityReady\":true,\"completed\":false}",
            null, null, null, null, Instant.now(), QualityStatus.VALID, 0, "trace-auto");
        when(snapshots.findBySnapshotIdAndTenantId("snap-auto-1", "tenant-A"))
            .thenReturn(Optional.of(snapshot));
        when(canonicalResources.findBySnapshotIdOrderBySeqNoAsc("snap-auto-1"))
            .thenReturn(List.of(patient));

        EvaluationRunResponse first = service.evaluateSnapshot(
            new EvaluationEvaluateSnapshotRequest("snap-auto-1", "DISCHARGE", "1.0.0"));
        EvaluationRunResponse replay = service.evaluateSnapshot(
            new EvaluationEvaluateSnapshotRequest("snap-auto-1", "DISCHARGE", "1.0.0"));

        assertThat(replay).isEqualTo(first);
        assertThat(runs.count()).isEqualTo(1);
        assertThat(results.count()).isEqualTo(1);
        assertThat(findings.count()).isEqualTo(1);
        assertThat(tasks.count()).isEqualTo(1);
        EvaluationRun savedRun = runs.findByRunIdAndTenantId(first.runId(), "tenant-A").orElseThrow();
        assertThat(savedRun.runCode()).startsWith("ER_AUTO_").hasSize("ER_AUTO_".length() + 16);
        assertThat(savedRun.inputDigest()).startsWith("sha256:");
        verify(ruleEvaluator, times(4)).evaluate(any(), any());
    }

    private QualityFinding finding(
            String findingId, QualityFindingSeverity severity, QualityFindingStatus status,
            String departmentId, Instant dueAt) {
        Instant now = Instant.now();
        return new QualityFinding(
            null, findingId, "tenant-A", "run-report", "result-report", "indicator-report",
            findingId, "整改报告测试问题", "用于验证整改报告聚合 SQL", severity, status,
            "整改报告真实聚合证据", departmentId, dueAt,
            now, "qa-1", now, "qa-1", "trace-real-flow");
    }

    private RectificationTask task(
            String taskId, String findingId, RectificationTaskStatus status,
            String departmentId, Instant dueAt) {
        Instant now = Instant.now();
        Instant closedAt = status == RectificationTaskStatus.CLOSED || status == RectificationTaskStatus.WAIVED
            ? now : null;
        return new RectificationTask(
            null, taskId, "tenant-A", findingId, departmentId, "head-1", status, dueAt,
            status == RectificationTaskStatus.SUBMITTED ? "已提交整改说明" : null,
            status == RectificationTaskStatus.SUBMITTED ? "proof-report" : null,
            status == RectificationTaskStatus.SUBMITTED ? now : null,
            status == RectificationTaskStatus.SUBMITTED ? "head-1" : null,
            closedAt, now, "qa-1", now, "qa-1", "trace-real-flow");
    }

    private String ruleDefinition(String fact, String operator, String valueLiteral) {
        return """
            {"all":[{"fact":"%s","operator":"%s","value":%s}]}
            """.formatted(fact, operator, valueLiteral);
    }

    private RuleDslEvaluation ruleEvaluation(boolean hit, String summary, String fact, boolean matched) {
        var explanation = new ObjectMapper().createObjectNode();
        explanation.put("summary", summary);
        var evidence = new ObjectMapper().createArrayNode();
        var item = new ObjectMapper().createObjectNode();
        item.put("fact", fact);
        item.put("sourcePath", "$." + fact);
        item.put("operator", "equals");
        item.put("matched", matched);
        item.put("missing", !matched);
        evidence.add(item);
        explanation.set("conditionEvidence", evidence);
        return new RuleDslEvaluation(hit, hit ? RuleRiskLevel.MEDIUM : null, List.of(), explanation);
    }

    @TestConfiguration
    static class JsonConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
