package com.medkernel.engine.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.evaluation.runtime.RuntimeReleaseEvaluationSelector;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutStrategy;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class EvaluationEngineServiceTest {

    private EvaluationIndicatorRepository indicators;
    private EvaluationRunRepository runs;
    private EvaluationResultRepository results;
    private QualityFindingRepository findings;
    private RectificationTaskRepository tasks;
    private RectificationReviewRepository reviews;
    private EvaluationIdempotencyKeyRepository idempotencyKeys;
    private AuditRecorder auditRecorder;
    private StateTransitionRecorder transitions;
    private DiagnoseResponseAssembler diagnoseAssembler;
    private com.medkernel.engine.context.CanonicalResourceRepository canonicalResources;
    private com.medkernel.engine.context.ContextSnapshotRepository snapshots;
    private com.medkernel.engine.rule.RuleDslEvaluator ruleEvaluator;
    private com.fasterxml.jackson.databind.ObjectMapper json;
    private EvaluationVersionedAssetAdapter versionedAssets;
    private AssetVersionRepository assetVersions;
    private ReleasePort releasePort;
    private com.medkernel.engine.org.OrgAssignmentValidator assignments;
    private RuntimeReleaseEvaluationSelector runtimeEvaluations;
    private EvaluationEngineService service;

    @BeforeEach
    void setUp() {
        indicators = mock(EvaluationIndicatorRepository.class);
        runs = mock(EvaluationRunRepository.class);
        results = mock(EvaluationResultRepository.class);
        findings = mock(QualityFindingRepository.class);
        tasks = mock(RectificationTaskRepository.class);
        reviews = mock(RectificationReviewRepository.class);
        idempotencyKeys = mock(EvaluationIdempotencyKeyRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        transitions = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        canonicalResources = mock(com.medkernel.engine.context.CanonicalResourceRepository.class);
        snapshots = mock(com.medkernel.engine.context.ContextSnapshotRepository.class);
        ruleEvaluator = mock(com.medkernel.engine.rule.RuleDslEvaluator.class);
        json = new com.fasterxml.jackson.databind.ObjectMapper();
        versionedAssets = mock(EvaluationVersionedAssetAdapter.class);
        assetVersions = mock(AssetVersionRepository.class);
        releasePort = mock(ReleasePort.class);
        assignments = mock(com.medkernel.engine.org.OrgAssignmentValidator.class);
        runtimeEvaluations = mock(RuntimeReleaseEvaluationSelector.class);

        service = new EvaluationEngineService(
            indicators, runs, results, findings, tasks, reviews, idempotencyKeys,
            auditRecorder, transitions, diagnoseAssembler,
            canonicalResources, snapshots, ruleEvaluator, json,
            versionedAssets, assetVersions, releasePort, assignments, runtimeEvaluations);

        when(indicators.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(runs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(results.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-eval", com.medkernel.shared.context.OrgScope.tenant("tenant-A"), "qa-1"));
        authenticate(RoleCode.ENGINE_OPERATOR);
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void indicatorFlowsFromDraftToActiveAndReplacesOldActiveVersion() {
        when(indicators.findTopByTenantIdAndIndicatorCodeOrderByVersionNoDesc(
            "tenant-A", "IND.VTE.PROPHYLAXIS"
        )).thenReturn(Optional.of(indicator("ei-v1", 1, EvaluationIndicatorStatus.OFFLINE)));
        EvaluationIndicator draft = service.createIndicator(indicatorRequest());
        assertThat(draft.status()).isEqualTo(EvaluationIndicatorStatus.DRAFT);
        assertThat(draft.tenantId()).isEqualTo("tenant-A");
        verify(versionedAssets).registerDraft(org.mockito.ArgumentMatchers.argThat(command ->
            command.assetType() == VersionedAssetType.EVALUATION
                && command.assetIdentity().equals("IND.VTE.PROPHYLAXIS")
                && command.organizationScope() == null
        ));

        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.EVALUATION, "IND.VTE.PROPHYLAXIS", "V2"
        )).thenReturn(Optional.of(assetVersion("av-eval-2", "V2", AssetVersionStatus.DRAFT)));
        when(indicators.findByIndicatorIdAndTenantId(draft.indicatorId(), "tenant-A"))
            .thenReturn(Optional.of(draft));
        EvaluationIndicator pending = service.submitIndicator(draft.indicatorId());
        assertThat(pending.status()).isEqualTo(EvaluationIndicatorStatus.PENDING_REVIEW);
        verify(releasePort).submitForReview(any());

        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.EVALUATION, "IND.VTE.PROPHYLAXIS", "V2"
        )).thenReturn(Optional.of(assetVersion("av-eval-2", "V2", AssetVersionStatus.DRAFT)));
        when(indicators.findByIndicatorIdAndTenantId(draft.indicatorId(), "tenant-A"))
            .thenReturn(Optional.of(pending));
        EvaluationIndicator published = service.publishIndicator(
            draft.indicatorId(),
            new EvaluationIndicatorReleaseRequest("质控办已复核指标口径")
        );
        assertThat(published.status()).isEqualTo(EvaluationIndicatorStatus.PUBLISHED);
        verify(releasePort).approveReview(any());

        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.EVALUATION, "IND.VTE.PROPHYLAXIS", "V2"
        )).thenReturn(Optional.of(assetVersion("av-eval-2", "V2", AssetVersionStatus.PUBLISHED)));
        when(indicators.findByIndicatorIdAndTenantId(draft.indicatorId(), "tenant-A"))
            .thenReturn(Optional.of(published));
        EvaluationIndicator gray = service.grayIndicator(
            draft.indicatorId(),
            new EvaluationIndicatorReleaseRequest("先按默认 10% 床位灰度")
        );
        assertThat(gray.status()).isEqualTo(EvaluationIndicatorStatus.GRAY);
        verify(releasePort).releaseGray(argThat(command ->
            command.rolloutPolicy().strategy() == RolloutStrategy.CANARY_BED_PERCENT
                && command.rolloutPolicy().bedPercent() == 10
        ));

        EvaluationIndicator oldActive = indicator("ei-old", 1, EvaluationIndicatorStatus.ACTIVE);
        when(indicators.findByIndicatorIdAndTenantId(draft.indicatorId(), "tenant-A"))
            .thenReturn(Optional.of(gray));
        when(indicators.findByTenantIdAndIndicatorCodeAndStatus(
            "tenant-A", "IND.VTE.PROPHYLAXIS", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(oldActive));
        EvaluationIndicator active = service.activateIndicator(
            draft.indicatorId(),
            new EvaluationIndicatorReleaseRequest("灰度观察通过，批准全量")
        );

        assertThat(active.status()).isEqualTo(EvaluationIndicatorStatus.ACTIVE);
        verify(releasePort).publish(any());
        ArgumentCaptor<EvaluationIndicator> saved = ArgumentCaptor.forClass(EvaluationIndicator.class);
        verify(indicators, org.mockito.Mockito.atLeast(5)).save(saved.capture());
        assertThat(saved.getAllValues())
            .anySatisfy(indicator -> {
                assertThat(indicator.indicatorId()).isEqualTo("ei-old");
                assertThat(indicator.status()).isEqualTo(EvaluationIndicatorStatus.OFFLINE);
            });
        verify(auditRecorder).record(AuditAction.PUBLISH, "evaluation_indicator",
            draft.indicatorId(), "发布评估指标 IND.VTE.PROPHYLAXIS");
    }

    @Test
    void createIndicatorAutomaticallyAllocatesNextBusinessVersion() {
        when(indicators.findTopByTenantIdAndIndicatorCodeOrderByVersionNoDesc(
            "tenant-A", "IND.VTE.PROPHYLAXIS"
        )).thenReturn(Optional.of(indicator("ei-v3", 3, EvaluationIndicatorStatus.OFFLINE)));

        EvaluationIndicator indicator = service.createIndicator(indicatorRequest());

        assertThat(indicator.versionNo()).isEqualTo(4);
        verify(versionedAssets).registerDraft(argThat(command ->
            command.assetIdentity().equals("IND.VTE.PROPHYLAXIS")
        ));
    }

    @Test
    void createIndicatorRequestDoesNotExposeBusinessVersionInput() {
        assertThat(EvaluationIndicatorCreateRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("versionNo");
    }

    @Test
    void createIndicatorReportsConcurrentVersionAllocationConflictHonestly() {
        when(indicators.save(any()))
            .thenThrow(new DuplicateKeyException("uk_eval_indicator_tenant_version"));

        assertThatThrownBy(() -> service.createIndicator(indicatorRequest()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("版本并发创建冲突")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void activateIndicatorRejectsUserWithoutQualityGovernanceResponsibility() {
        EvaluationIndicator gray = indicator("ei-gray", 2, EvaluationIndicatorStatus.GRAY);
        when(indicators.findByIndicatorIdAndTenantId("ei-gray", "tenant-A"))
            .thenReturn(Optional.of(gray));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.EVALUATION, "IND.VTE.PROPHYLAXIS", "V2"
        )).thenReturn(Optional.of(assetVersion("av-eval-2", "V2", AssetVersionStatus.PUBLISHED)));
        authenticate(RoleCode.CLINICAL_USER);

        assertThatThrownBy(() -> service.activateIndicator(
            "ei-gray",
            new EvaluationIndicatorReleaseRequest("请求体不再携带可伪造角色")
        ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void indicatorRejectsInvalidStateTransition() {
        EvaluationIndicator active = indicator("ei-active", 1, EvaluationIndicatorStatus.ACTIVE);
        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A"))
            .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.submitIndicator("ei-active"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_003);
    }

    @Test
    void createIndicatorRejectsNaturalLanguageRuleDefinitionBeforeDrafting() {
        EvaluationIndicatorCreateRequest request = new EvaluationIndicatorCreateRequest(
            "IND.QC.RULE", "质控规则指标", EvaluationSubjectType.MEDICAL_RECORD,
            "符合住院风险分层病例",
            ruleDefinition("patient.qualityReady", "equals", "true"),
            null, null, "DISCHARGE+24H", "全院", "dept-1", "guideline-1");

        assertThatThrownBy(() -> service.createIndicator(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_001);

        verify(indicators, never()).save(any());
    }

    @Test
    void runRecordsResultAndCreatesTaskForHighRiskFinding() {
        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A"))
            .thenReturn(Optional.of(indicator("ei-active", 1, EvaluationIndicatorStatus.ACTIVE)));

        EvaluationRunResponse response = service.run(runRequest(QualityFindingSeverity.P1, true));

        assertThat(response.status()).isEqualTo(EvaluationRunStatus.RECORDED);
        assertThat(response.resultCount()).isEqualTo(1);
        assertThat(response.findingCount()).isEqualTo(1);
        assertThat(response.taskCount()).isEqualTo(1);

        ArgumentCaptor<EvaluationResult> result = ArgumentCaptor.forClass(EvaluationResult.class);
        ArgumentCaptor<QualityFinding> finding = ArgumentCaptor.forClass(QualityFinding.class);
        ArgumentCaptor<RectificationTask> task = ArgumentCaptor.forClass(RectificationTask.class);
        verify(results).save(result.capture());
        verify(findings).save(finding.capture());
        verify(tasks).save(task.capture());
        assertThat(result.getValue().indicatorVersion()).isEqualTo(1);
        assertThat(finding.getValue().status()).isEqualTo(QualityFindingStatus.ASSIGNED);
        assertThat(task.getValue().status()).isEqualTo(RectificationTaskStatus.ASSIGNED);
        verify(auditRecorder).record(AuditAction.EXECUTE, "evaluation_run",
            response.runId(), "接收评估运行 RUN.VTE");
    }

    @Test
    void runRejectsNonActiveIndicatorAndIncompleteHighRiskFinding() {
        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A"))
            .thenReturn(Optional.of(indicator("ei-active", 1, EvaluationIndicatorStatus.DRAFT)));
        assertThatThrownBy(() -> service.run(runRequest(QualityFindingSeverity.P2, false)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_004);

        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A"))
            .thenReturn(Optional.of(indicator("ei-active", 1, EvaluationIndicatorStatus.ACTIVE)));
        assertThatThrownBy(() -> service.run(runRequest(QualityFindingSeverity.P0, false)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_006);
    }

    @Test
    void lowerRiskFindingWithoutAssignmentRemainsNew() {
        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A"))
            .thenReturn(Optional.of(indicator("ei-active", 1, EvaluationIndicatorStatus.ACTIVE)));

        service.run(runRequest(QualityFindingSeverity.P3, false));

        ArgumentCaptor<QualityFinding> finding = ArgumentCaptor.forClass(QualityFinding.class);
        verify(findings).save(finding.capture());
        assertThat(finding.getValue().status()).isEqualTo(QualityFindingStatus.NEW);
        verify(tasks, never()).save(any());
    }

    @Test
    void runRejectsFactWithoutContextOrExplicitManualSource() {
        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A"))
            .thenReturn(Optional.of(indicator("ei-active", 1, EvaluationIndicatorStatus.ACTIVE)));
        EvaluationResultRequest result = new EvaluationResultRequest(
            "ei-active", EvaluationSubjectType.MEDICAL_RECORD, "record-1", BigDecimal.ONE,
            EvaluationResultLevel.PASS, false, "抽检通过", null, null, List.of());
        EvaluationRunRequest unlinked = new EvaluationRunRequest(
            "RUN.UNLINKED", EvaluationRunType.BATCH_IMPORT, null, null, null, null,
            "DISCHARGE", null, "sha256:run", Instant.now(), List.of(result));

        assertThatThrownBy(() -> service.run(unlinked))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_001);
    }

    @Test
    void rectificationSubmissionAndApprovalCloseFinding() {
        QualityFinding assigned = finding("qf-1", QualityFindingSeverity.P1, QualityFindingStatus.ASSIGNED);
        RectificationTask task = task("task-1", RectificationTaskStatus.ASSIGNED);
        when(findings.findByFindingIdAndTenantId("qf-1", "tenant-A")).thenReturn(Optional.of(assigned));
        when(tasks.findByFindingIdAndTenantId("qf-1", "tenant-A")).thenReturn(Optional.of(task));

        RectificationResponse submitted = service.submitRectification(
            "qf-1", new RectificationSubmitRequest("补录风险评估记录", "proof-1"));
        assertThat(submitted.findingStatus()).isEqualTo(QualityFindingStatus.REMEDIATING);
        assertThat(submitted.taskStatus()).isEqualTo(RectificationTaskStatus.SUBMITTED);
        verify(auditRecorder).record(AuditAction.UPDATE, "quality_finding", "qf-1",
            "提交质量问题整改 task-1");

        QualityFinding remediating = finding("qf-1", QualityFindingSeverity.P1, QualityFindingStatus.REMEDIATING);
        RectificationTask submittedTask = task("task-1", RectificationTaskStatus.SUBMITTED);
        when(findings.findByFindingIdAndTenantId("qf-1", "tenant-A")).thenReturn(Optional.of(remediating));
        when(tasks.findByFindingIdAndTenantId("qf-1", "tenant-A")).thenReturn(Optional.of(submittedTask));

        RectificationReviewResponse approved = service.reviewRectification(
            "qf-1", new RectificationReviewRequest(
                RectificationReviewDecision.APPROVED, "证据充分，允许闭环", "review-proof-1"));
        assertThat(approved.findingStatus()).isEqualTo(QualityFindingStatus.CLOSED);
        assertThat(approved.taskStatus()).isEqualTo(RectificationTaskStatus.CLOSED);
        verify(reviews).save(any(RectificationReview.class));
        verify(auditRecorder).record(AuditAction.REVIEW, "quality_finding", "qf-1",
            "复核质量问题整改 APPROVED");
    }

    @Test
    void p0FindingCannotBeWaivedByOrdinaryReview() {
        when(findings.findByFindingIdAndTenantId("qf-p0", "tenant-A"))
            .thenReturn(Optional.of(finding("qf-p0", QualityFindingSeverity.P0, QualityFindingStatus.REMEDIATING)));
        when(tasks.findByFindingIdAndTenantId("qf-p0", "tenant-A"))
            .thenReturn(Optional.of(task("task-p0", RectificationTaskStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.reviewRectification("qf-p0", new RectificationReviewRequest(
                RectificationReviewDecision.WAIVED, "申请豁免", null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("P0 质量问题不得通过普通复核豁免")
            .hasMessageNotContaining("质控问题")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_007);
    }

    @Test
    void dispatchRectificationCreatesTaskForNewFindingAndRejectsChangedReplay() {
        QualityFinding newFinding = finding("qf-new", QualityFindingSeverity.P2, QualityFindingStatus.NEW);
        when(findings.findByFindingIdAndTenantId("qf-new", "tenant-A")).thenReturn(Optional.of(newFinding));
        when(tasks.findByFindingIdAndTenantId("qf-new", "tenant-A")).thenReturn(Optional.empty());

        Instant dueAt = Instant.now().plusSeconds(86400);
        RectificationResponse response = service.dispatchRectification(
            new RectificationDispatchRequest("qf-new", "dept-quality", "head-quality", dueAt),
            "idem-dispatch-1");

        assertThat(response.findingStatus()).isEqualTo(QualityFindingStatus.ASSIGNED);
        assertThat(response.taskStatus()).isEqualTo(RectificationTaskStatus.ASSIGNED);
        ArgumentCaptor<RectificationTask> task = ArgumentCaptor.forClass(RectificationTask.class);
        verify(tasks).save(task.capture());
        assertThat(task.getValue().findingId()).isEqualTo("qf-new");
        assertThat(task.getValue().responsibleDepartmentId()).isEqualTo("dept-quality");
        assertThat(task.getValue().assigneeUserId()).isEqualTo("head-quality");
        verify(assignments).requireActiveDepartment("dept-quality");
        verify(assignments).requireActiveUserIfPresent("head-quality");
        verify(auditRecorder).record(AuditAction.CREATE, "rectification_task",
            task.getValue().taskId(), "派发质量问题整改 qf-new");

        RectificationTask existing = new RectificationTask(
            task.getValue().id(), task.getValue().taskId(), task.getValue().tenantId(),
            task.getValue().findingId(), task.getValue().responsibleDepartmentId(),
            task.getValue().assigneeUserId(), task.getValue().status(), task.getValue().dueAt(),
            task.getValue().rectificationSummary(), task.getValue().evidenceRef(),
            task.getValue().submittedAt(), task.getValue().submittedBy(), task.getValue().closedAt(),
            task.getValue().createdAt(), task.getValue().createdBy(), task.getValue().updatedAt(),
            task.getValue().updatedBy(), task.getValue().traceId());
        when(tasks.findByFindingIdAndTenantId("qf-new", "tenant-A")).thenReturn(Optional.of(existing));
        assertThat(service.dispatchRectification(
            new RectificationDispatchRequest("qf-new", "dept-quality", "head-quality", dueAt),
            "idem-dispatch-1").taskId()).isEqualTo(existing.taskId());

        assertThatThrownBy(() -> service.dispatchRectification(
                new RectificationDispatchRequest("qf-new", "dept-other", "head-quality", dueAt),
                "idem-dispatch-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_008);
    }

    @Test
    void returnedReviewRequiresReasonBeforeSendingTaskBack() {
        when(findings.findByFindingIdAndTenantId("qf-1", "tenant-A"))
            .thenReturn(Optional.of(finding("qf-1", QualityFindingSeverity.P1, QualityFindingStatus.REMEDIATING)));
        when(tasks.findByFindingIdAndTenantId("qf-1", "tenant-A"))
            .thenReturn(Optional.of(task("task-1", RectificationTaskStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.reviewRectification("qf-1", new RectificationReviewRequest(
                RectificationReviewDecision.RETURNED, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_007);

        verify(reviews, never()).save(any());
    }

    @Test
    void waiveRectificationTaskRequiresApprovalReferenceAndLeavesReviewEvidence() {
        when(tasks.findByTaskIdAndTenantId("task-1", "tenant-A"))
            .thenReturn(Optional.of(task("task-1", RectificationTaskStatus.SUBMITTED)));
        when(tasks.findByFindingIdAndTenantId("qf-1", "tenant-A"))
            .thenReturn(Optional.of(task("task-1", RectificationTaskStatus.SUBMITTED)));
        when(findings.findByFindingIdAndTenantId("qf-1", "tenant-A"))
            .thenReturn(Optional.of(finding("qf-1", QualityFindingSeverity.P2, QualityFindingStatus.REMEDIATING)));

        assertThatThrownBy(() -> service.waiveRectificationTask(
                "task-1", new RectificationWaiveRequest("院级审批同意按豁免处理", null, "proof-waive"),
                "idem-waive-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_001);

        RectificationReviewResponse response = service.waiveRectificationTask(
            "task-1", new RectificationWaiveRequest("院级审批同意按豁免处理", "approval-2026-001", "proof-waive"),
            "idem-waive-1");

        assertThat(response.findingStatus()).isEqualTo(QualityFindingStatus.WAIVED);
        assertThat(response.taskStatus()).isEqualTo(RectificationTaskStatus.WAIVED);
        ArgumentCaptor<RectificationReview> review = ArgumentCaptor.forClass(RectificationReview.class);
        ArgumentCaptor<RectificationTask> reviewedTask = ArgumentCaptor.forClass(RectificationTask.class);
        verify(reviews).save(review.capture());
        verify(tasks).save(reviewedTask.capture());
        assertThat(review.getValue().decision()).isEqualTo(RectificationReviewDecision.WAIVED);
        assertThat(review.getValue().comment()).contains("院级审批同意按豁免处理");
        assertThat(review.getValue().evidenceRef()).contains("approval-2026-001").contains("proof-waive");
        assertThat(reviewedTask.getValue().closedAt()).isNotNull();
    }

    @Test
    void rectificationReportAggregatesRealTaskAndSafetyFacts() {
        Instant now = Instant.now();
        when(tasks.countByTenantIdAndDepartmentFilter("tenant-A", "dept-1")).thenReturn(10L);
        when(tasks.countOpenByTenantIdAndDepartmentFilter("tenant-A", "dept-1")).thenReturn(4L);
        when(tasks.countClosedByTenantIdAndDepartmentFilter("tenant-A", "dept-1")).thenReturn(5L);
        when(tasks.countWaivedByTenantIdAndDepartmentFilter("tenant-A", "dept-1")).thenReturn(1L);
        when(tasks.countOverdueOpenByTenantIdAndDepartmentFilter(eq("tenant-A"), eq("dept-1"), any()))
            .thenReturn(2L);
        when(tasks.countOpenP0ByTenantIdAndDepartmentFilter("tenant-A", "dept-1")).thenReturn(1L);

        RectificationReportResponse report =
            service.rectificationReport(new RectificationReportFilter("dept-1"), now);

        assertThat(report.status()).isEqualTo(RectificationReportStatus.AVAILABLE);
        assertThat(report.totalTasks()).isEqualTo(10);
        assertThat(report.openTasks()).isEqualTo(4);
        assertThat(report.closedTasks()).isEqualTo(5);
        assertThat(report.waivedTasks()).isEqualTo(1);
        assertThat(report.overdueTasks()).isEqualTo(2);
        assertThat(report.highPriorityOpenTasks()).isEqualTo(1);
        assertThat(report.closureRate()).isEqualByComparingTo("0.5000");
        assertThat(report.sourceTable()).isEqualTo("rectification_task");
    }

    @Test
    void diagnoseAssemblesRunRelatedFacts() {
        EvaluationRun run = evaluationRun("run-1");
        EvaluationResult result = result("result-1", "run-1");
        QualityFinding finding = finding("qf-1", QualityFindingSeverity.P1, QualityFindingStatus.ASSIGNED);
        RectificationTask task = task("task-1", RectificationTaskStatus.ASSIGNED);
        DiagnoseResponse expected = new DiagnoseResponse(
            "evaluation_run", "run-1", "tenant-A", "RECORDED",
            run, List.of(), List.of(),
            Map.of("results", List.of("result-1"), "findings", List.of("qf-1"), "tasks", List.of("task-1")),
            null, "trace-eval", null);
        when(runs.findByRunIdAndTenantId("run-1", "tenant-A")).thenReturn(Optional.of(run));
        when(results.findByRunIdAndTenantIdOrderByCreatedAtAsc("run-1", "tenant-A")).thenReturn(List.of(result));
        when(findings.findByResultIdAndTenantIdOrderByCreatedAtAsc("result-1", "tenant-A"))
            .thenReturn(List.of(finding));
        when(tasks.findByFindingIdAndTenantId("qf-1", "tenant-A")).thenReturn(Optional.of(task));
        when(diagnoseAssembler.assemble(eq("evaluation_run"), eq("run-1"), eq("tenant-A"),
            eq("RECORDED"), eq(run), eq(List.of()), any(), any(), eq("trace-eval")))
            .thenReturn(expected);

        assertThat(service.diagnose("run-1")).isSameAs(expected);
    }

    private EvaluationIndicatorCreateRequest indicatorRequest() {
        return new EvaluationIndicatorCreateRequest(
            "IND.VTE.PROPHYLAXIS", "静脉血栓预防完成率", EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            ruleDefinition("patient.excluded", "equals", "true"),
            "达标率 >= 95%",
            "DISCHARGE+24H", "全院住院科室", "dept-1", "guideline-1");
    }

    private EvaluationRunRequest runRequest(QualityFindingSeverity severity, boolean assigned) {
        QualityFindingRequest finding = new QualityFindingRequest(
            "FIND.VTE.001", "未完成静脉血栓风险评估", "出院前未记录风险评估", severity,
            "缺少风险评估记录",
            assigned ? "dept-1" : null,
            assigned ? Instant.now().plusSeconds(86400) : null,
            assigned ? "head-1" : null);
        EvaluationResultRequest result = new EvaluationResultRequest(
            "ei-active", EvaluationSubjectType.MEDICAL_RECORD, "record-1", new BigDecimal("70.5000"),
            EvaluationResultLevel.NON_COMPLIANT, true, "指标未达标", "evidence-1", "dept-1",
            List.of(finding));
        return new EvaluationRunRequest(
            "RUN.VTE", EvaluationRunType.UPSTREAM_RESULT, "event-1", null,
            "patient-1", "enc-1", "DISCHARGE", null, "sha256:run", Instant.now(), List.of(result));
    }

    private EvaluationIndicator indicator(String indicatorId, int version, EvaluationIndicatorStatus status) {
        Instant now = Instant.now();
        return new EvaluationIndicator(
            null, indicatorId, "tenant-A", "IND.VTE.PROPHYLAXIS", version, "静脉血栓预防完成率",
            EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            null, null,
            "DISCHARGE+24H", "全院", "dept-1", "guideline-1", status,
            now, "qa-1", status == EvaluationIndicatorStatus.ACTIVE ? now : null,
            now, "qa-1", now, "qa-1", "trace-eval");
    }

    private EvaluationRun evaluationRun(String runId) {
        Instant now = Instant.now();
        return new EvaluationRun(
            null, runId, "tenant-A", "RUN.VTE", EvaluationRunType.UPSTREAM_RESULT,
            "event-1", "snapshot-1", "patient-1", "enc-1", "DISCHARGE", "runtime-release-test",
            "sha256:run", EvaluationRunStatus.RECORDED, null, now,
            now, "qa-1", now, "qa-1", "trace-eval");
    }

    private EvaluationResult result(String resultId, String runId) {
        Instant now = Instant.now();
        return new EvaluationResult(
            null, resultId, "tenant-A", runId, "ei-active", "IND.VTE.PROPHYLAXIS", 1,
            EvaluationSubjectType.MEDICAL_RECORD, "record-1", BigDecimal.ONE,
            EvaluationResultLevel.NON_COMPLIANT, true, "未达标", "evidence-1", "dept-1",
            now, "qa-1", now, "qa-1", "trace-eval");
    }

    private QualityFinding finding(String findingId, QualityFindingSeverity severity, QualityFindingStatus status) {
        Instant now = Instant.now();
        return new QualityFinding(
            null, findingId, "tenant-A", "run-1", "result-1", "ei-active", "FIND.VTE.001",
            "未完成静脉血栓风险评估", "出院前未记录风险评估", severity, status,
            "缺少风险评估记录", "dept-1", now.plusSeconds(86400),
            now, "qa-1", now, "qa-1", "trace-eval");
    }

    private RectificationTask task(String taskId, RectificationTaskStatus status) {
        Instant now = Instant.now();
        return new RectificationTask(
            null, taskId, "tenant-A", "qf-1", "dept-1", "head-1", status,
            now.plusSeconds(86400), null, null, null, null, null,
            now, "qa-1", now, "qa-1", "trace-eval");
    }

    @Test
    void evaluateSnapshotCalculatesMetricsAndCreatesDefectFindings() {
        // Mock ContextSnapshot
        ContextSnapshot snapshot = new ContextSnapshot(
            null, "snap-1", "tenant-A", "dept-1", null, null, "runtime-release-test",
            "patient-1", "enc-1", com.medkernel.engine.context.ContextSnapshotStatus.ACTIVE,
            "[]", "{}",
            "{}", com.medkernel.engine.context.QualityStatus.VALID, "trace-eval",
            "sig", Instant.now(), "qa-1");
        when(snapshots.findBySnapshotIdAndTenantId("snap-1", "tenant-A")).thenReturn(Optional.of(snapshot));

        // Mock CanonicalResource (Patient data)
        CanonicalResource patientRes = new CanonicalResource(
            null, "res-1", "snap-1", "tenant-A", com.medkernel.engine.context.CanonicalResourceType.PATIENT,
            "{\"patientId\":\"patient-1\",\"name\":\"张三\"}", null, null, null, null, Instant.now(),
            com.medkernel.engine.context.QualityStatus.VALID, 0, "trace-eval");
        when(canonicalResources.findBySnapshotIdOrderBySeqNoAsc("snap-1"))
            .thenReturn(List.of(patientRes));

        // Mock Active Indicator
        EvaluationIndicator indicator = new EvaluationIndicator(
            null, "ei-active", "tenant-A", "IND.VTE.PROPHYLAXIS", 1, "静脉血栓预防完成率",
            EvaluationSubjectType.MEDICAL_RECORD, "{\"all\":[]}", "{\"all\":[]}", "{\"all\":[]}",
            "P1级严重质控缺陷", "DISCHARGE+24H", "全院", "dept-1", "guideline-1",
            EvaluationIndicatorStatus.ACTIVE, Instant.now(), "qa-1", Instant.now(), Instant.now(),
            "qa-1", Instant.now(), "qa-1", "trace-eval");
        when(indicators.findByTenantIdAndStatus("tenant-A", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(indicator));
        when(runtimeEvaluations.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(indicator));
        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A")).thenReturn(Optional.of(indicator));

        // Mock ruleEvaluator.evaluateConditionTree
        // 1. 分母校验：返回命中  2. 排除校验：不命中  3. 分子校验：不命中
        when(ruleEvaluator.evaluateConditionTree(any(), any(), any()))
            .thenReturn(new RuleDslEvaluation(true, com.medkernel.engine.rule.RuleRiskLevel.MEDIUM, List.of(), null))
            .thenReturn(new RuleDslEvaluation(false, null, List.of(), null))
            .thenReturn(new RuleDslEvaluation(false, null, List.of(), null));

        EvaluationRunResponse response = service.evaluateSnapshot(
            new EvaluationEvaluateSnapshotRequest("snap-1", "DISCHARGE"));

        assertThat(response.status()).isEqualTo(EvaluationRunStatus.RECORDED);
        assertThat(response.resultCount()).isEqualTo(1);
        assertThat(response.findingCount()).isEqualTo(1);
        assertThat(response.taskCount()).isEqualTo(1);

        verify(runs).save(any());
        verify(results).save(any());
        verify(findings).save(any());
        verify(tasks).save(any());
    }

    @Test
    void evaluateSnapshotPersistsRuleExplanationIntoResultAndFindingEvidence() {
        ContextSnapshot snapshot = snapshot("snap-1");
        when(snapshots.findBySnapshotIdAndTenantId("snap-1", "tenant-A")).thenReturn(Optional.of(snapshot));
        when(canonicalResources.findBySnapshotIdOrderBySeqNoAsc("snap-1"))
            .thenReturn(List.of(patientResource(
                "res-1", "{\"patientId\":\"patient-1\",\"qualityReady\":true}")));

        EvaluationIndicator indicator = new EvaluationIndicator(
            null, "ei-active", "tenant-A", "IND.QC.EXPLAIN", 3, "出院质量记录完整率",
            EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            null, "P1级严重质控缺陷", "DISCHARGE+24H", "全院", "dept-1", "guideline-1",
            EvaluationIndicatorStatus.ACTIVE, Instant.now(), "qa-1", Instant.now(), Instant.now(),
            "qa-1", Instant.now(), "qa-1", "trace-eval");
        when(indicators.findByTenantIdAndStatus("tenant-A", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(indicator));
        when(runtimeEvaluations.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(indicator));
        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A")).thenReturn(Optional.of(indicator));
        when(ruleEvaluator.evaluateConditionTree(any(), any(), any()))
            .thenReturn(ruleEvaluation(true, "分母入组规则校验", "patient.qualityReady", true))
            .thenReturn(ruleEvaluation(false, "分子达标规则校验", "patient.completed", false));

        service.evaluateSnapshot(new EvaluationEvaluateSnapshotRequest("snap-1", "DISCHARGE"));

        ArgumentCaptor<EvaluationResult> result = ArgumentCaptor.forClass(EvaluationResult.class);
        ArgumentCaptor<QualityFinding> finding = ArgumentCaptor.forClass(QualityFinding.class);
        verify(results).save(result.capture());
        verify(findings).save(finding.capture());
        assertThat(result.getValue().evidenceSummary())
            .contains("分母入组规则校验")
            .contains("patient.qualityReady")
            .contains("分子达标规则校验")
            .contains("patient.completed");
        assertThat(finding.getValue().evidenceSummary())
            .contains("系统自动评估扫描质量证据支撑")
            .contains("分子达标规则校验")
            .contains("patient.completed")
            .doesNotContain("质控证据");
    }

    @Test
    void evaluateSnapshotUsesStableRunCodeAndDigestForSameSnapshotAndIndicatorVersion() {
        ContextSnapshot snapshot = snapshot("snap-1");
        when(snapshots.findBySnapshotIdAndTenantId("snap-1", "tenant-A")).thenReturn(Optional.of(snapshot));
        when(canonicalResources.findBySnapshotIdOrderBySeqNoAsc("snap-1"))
            .thenReturn(List.of(patientResource(
                "res-1", "{\"patientId\":\"patient-1\",\"qualityReady\":true,\"completed\":true}")));

        EvaluationIndicator indicator = new EvaluationIndicator(
            null, "ei-active", "tenant-A", "IND.QC.REPLAY", 4, "出院质量完成率",
            EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            null, "P1级严重质控缺陷", "DISCHARGE+24H", "全院", "dept-1", "guideline-1",
            EvaluationIndicatorStatus.ACTIVE, Instant.now(), "qa-1", Instant.now(), Instant.now(),
            "qa-1", Instant.now(), "qa-1", "trace-eval");
        when(indicators.findByTenantIdAndStatus("tenant-A", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(indicator));
        when(runtimeEvaluations.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(indicator));
        when(indicators.findByIndicatorIdAndTenantId("ei-active", "tenant-A")).thenReturn(Optional.of(indicator));
        when(ruleEvaluator.evaluateConditionTree(any(), any(), any()))
            .thenReturn(ruleEvaluation(true, "分母入组规则校验", "patient.qualityReady", true))
            .thenReturn(ruleEvaluation(true, "分子达标规则校验", "patient.completed", true))
            .thenReturn(ruleEvaluation(true, "分母入组规则校验", "patient.qualityReady", true))
            .thenReturn(ruleEvaluation(true, "分子达标规则校验", "patient.completed", true));

        service.evaluateSnapshot(new EvaluationEvaluateSnapshotRequest("snap-1", "DISCHARGE"));
        service.evaluateSnapshot(new EvaluationEvaluateSnapshotRequest("snap-1", "DISCHARGE"));

        ArgumentCaptor<EvaluationRun> run = ArgumentCaptor.forClass(EvaluationRun.class);
        verify(runs, org.mockito.Mockito.times(2)).save(run.capture());
        assertThat(run.getAllValues())
            .extracting(EvaluationRun::runCode)
            .containsExactly(run.getAllValues().getFirst().runCode(), run.getAllValues().getFirst().runCode());
        assertThat(run.getAllValues())
            .extracting(EvaluationRun::inputDigest)
            .containsExactly(run.getAllValues().getFirst().inputDigest(), run.getAllValues().getFirst().inputDigest());
        assertThat(run.getAllValues().getFirst().runCode()).startsWith("ER_AUTO_");
        assertThat(run.getAllValues().getFirst().inputDigest()).startsWith("sha256:");
    }

    @Test
    void evaluateSnapshotReplaysExistingStableRunWithoutDuplicatingFacts() {
        ContextSnapshot snapshot = snapshot("snap-1");
        when(snapshots.findBySnapshotIdAndTenantId("snap-1", "tenant-A")).thenReturn(Optional.of(snapshot));
        when(canonicalResources.findBySnapshotIdOrderBySeqNoAsc("snap-1"))
            .thenReturn(List.of(patientResource(
                "res-1", "{\"patientId\":\"patient-1\",\"qualityReady\":true,\"completed\":true}")));

        EvaluationIndicator indicator = new EvaluationIndicator(
            null, "ei-active", "tenant-A", "IND.QC.REPLAY", 4, "出院质量完成率",
            EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            null, "P1级严重质控缺陷", "DISCHARGE+24H", "全院", "dept-1", "guideline-1",
            EvaluationIndicatorStatus.ACTIVE, Instant.now(), "qa-1", Instant.now(), Instant.now(),
            "qa-1", Instant.now(), "qa-1", "trace-eval");
        when(indicators.findByTenantIdAndStatus("tenant-A", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(indicator));
        when(runtimeEvaluations.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(indicator));

        EvaluationResult existingResult = result("result-existing", "er-existing");
        QualityFinding existingFinding = finding("qf-existing", QualityFindingSeverity.P1, QualityFindingStatus.ASSIGNED);
        RectificationTask existingTask = task("task-existing", RectificationTaskStatus.ASSIGNED);
        when(runs.findByRunCodeAndTenantId(any(), eq("tenant-A"))).thenAnswer(invocation -> {
            String stableRunCode = invocation.getArgument(0);
            return Optional.of(new EvaluationRun(
                null, "er-existing", "tenant-A", stableRunCode, EvaluationRunType.UPSTREAM_RESULT,
                null, "snap-1", "patient-1", "enc-1", "DISCHARGE", "runtime-release-test",
                "sha256:existing", EvaluationRunStatus.RECORDED, null, Instant.now(),
                Instant.now(), "qa-1", Instant.now(), "qa-1", "trace-existing"));
        });
        when(results.findByRunIdAndTenantIdOrderByCreatedAtAsc("er-existing", "tenant-A"))
            .thenReturn(List.of(existingResult));
        when(findings.findByResultIdAndTenantIdOrderByCreatedAtAsc("result-existing", "tenant-A"))
            .thenReturn(List.of(existingFinding));
        when(tasks.findByFindingIdAndTenantId("qf-existing", "tenant-A"))
            .thenReturn(Optional.of(existingTask));

        EvaluationRunResponse response = service.evaluateSnapshot(
            new EvaluationEvaluateSnapshotRequest("snap-1", "DISCHARGE"));

        assertThat(response.runId()).isEqualTo("er-existing");
        assertThat(response.resultCount()).isEqualTo(1);
        assertThat(response.findingCount()).isEqualTo(1);
        assertThat(response.taskCount()).isEqualTo(1);
        assertThat(response.traceId()).isEqualTo("trace-existing");
        ArgumentCaptor<String> replayRunCode = ArgumentCaptor.forClass(String.class);
        verify(runs).findByRunCodeAndTenantId(replayRunCode.capture(), eq("tenant-A"));
        assertThat(replayRunCode.getValue())
            .startsWith("ER_AUTO_")
            .hasSize("ER_AUTO_".length() + 16);
        verify(ruleEvaluator, never()).evaluateConditionTree(any(), any(), any());
        verify(runs, never()).save(any());
        verify(results, never()).save(any());
        verify(findings, never()).save(any());
        verify(tasks, never()).save(any());
    }

    @Test
    void evaluateSnapshotRejectsMalformedCanonicalResourcePayload() {
        ContextSnapshot snapshot = snapshot("snap-1");
        when(snapshots.findBySnapshotIdAndTenantId("snap-1", "tenant-A")).thenReturn(Optional.of(snapshot));

        CanonicalResource badPatient = new CanonicalResource(
            null, "res-bad", "snap-1", "tenant-A", com.medkernel.engine.context.CanonicalResourceType.PATIENT,
            "{bad-json", null, null, null, null, Instant.now(),
            com.medkernel.engine.context.QualityStatus.VALID, 0, "trace-eval");
        when(canonicalResources.findBySnapshotIdOrderBySeqNoAsc("snap-1"))
            .thenReturn(List.of(badPatient));

        assertThatThrownBy(() -> service.evaluateSnapshot(
                new EvaluationEvaluateSnapshotRequest("snap-1", "DISCHARGE")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_001);

        verify(ruleEvaluator, never()).evaluateConditionTree(any(), any(), any());
        verify(runs, never()).save(any());
    }

    @Test
    void evaluateSnapshotRejectsMalformedIndicatorDslBeforePersistingRun() {
        ContextSnapshot snapshot = snapshot("snap-1");
        when(snapshots.findBySnapshotIdAndTenantId("snap-1", "tenant-A")).thenReturn(Optional.of(snapshot));

        CanonicalResource patientRes = new CanonicalResource(
            null, "res-1", "snap-1", "tenant-A", com.medkernel.engine.context.CanonicalResourceType.PATIENT,
            "{\"patientId\":\"patient-1\"}", null, null, null, null, Instant.now(),
            com.medkernel.engine.context.QualityStatus.VALID, 0, "trace-eval");
        when(canonicalResources.findBySnapshotIdOrderBySeqNoAsc("snap-1"))
            .thenReturn(List.of(patientRes));

        EvaluationIndicator indicator = new EvaluationIndicator(
            null, "ei-active", "tenant-A", "IND.VTE.PROPHYLAXIS", 1, "静脉血栓预防完成率",
            EvaluationSubjectType.MEDICAL_RECORD, "{bad-json", "{\"all\":[]}", null,
            "P1级严重质控缺陷", "DISCHARGE+24H", "全院", "dept-1", "guideline-1",
            EvaluationIndicatorStatus.ACTIVE, Instant.now(), "qa-1", Instant.now(), Instant.now(),
            "qa-1", Instant.now(), "qa-1", "trace-eval");
        when(indicators.findByTenantIdAndStatus("tenant-A", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(indicator));
        when(runtimeEvaluations.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(indicator));

        assertThatThrownBy(() -> service.evaluateSnapshot(
                new EvaluationEvaluateSnapshotRequest("snap-1", "DISCHARGE")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_EVAL_001);

        verify(runs, never()).save(any());
    }

    @Test
    void evaluateSnapshotOnlyUsesIndicatorsPinnedByCurrentRuntimeRelease() {
        ContextSnapshot snapshot = snapshot("snap-1");
        when(snapshots.findBySnapshotIdAndTenantId("snap-1", "tenant-A")).thenReturn(Optional.of(snapshot));
        when(canonicalResources.findBySnapshotIdOrderBySeqNoAsc("snap-1"))
            .thenReturn(List.of(patientResource(
                "res-1", "{\"patientId\":\"patient-1\",\"qualityReady\":true,\"completed\":true}")));

        EvaluationIndicator included = new EvaluationIndicator(
            null, "ei-runtime", "tenant-A", "IND.QC.RUNTIME", 2, "机构生效版本内指标",
            EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            null, "P1级严重质控缺陷", "DISCHARGE+24H", "全院", "dept-1", "guideline-1",
            EvaluationIndicatorStatus.ACTIVE, Instant.now(), "qa-1", Instant.now(), Instant.now(),
            "qa-1", Instant.now(), "qa-1", "trace-eval");
        EvaluationIndicator outside = new EvaluationIndicator(
            null, "ei-outside", "tenant-A", "IND.QC.OUTSIDE", 1, "未上线指标",
            EvaluationSubjectType.MEDICAL_RECORD,
            ruleDefinition("patient.qualityReady", "equals", "true"),
            ruleDefinition("patient.completed", "equals", "true"),
            null, "P1级严重质控缺陷", "DISCHARGE+24H", "全院", "dept-1", "guideline-1",
            EvaluationIndicatorStatus.ACTIVE, Instant.now(), "qa-1", Instant.now(), Instant.now(),
            "qa-1", Instant.now(), "qa-1", "trace-eval");
        when(indicators.findByTenantIdAndStatus("tenant-A", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(included, outside));
        when(runtimeEvaluations.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(included));
        when(indicators.findByIndicatorIdAndTenantId("ei-runtime", "tenant-A"))
            .thenReturn(Optional.of(included));
        when(ruleEvaluator.evaluateConditionTree(any(), any(), any()))
            .thenReturn(ruleEvaluation(true, "分母入组规则校验", "patient.qualityReady", true))
            .thenReturn(ruleEvaluation(true, "分子达标规则校验", "patient.completed", true));

        EvaluationRunResponse response = service.evaluateSnapshot(
            new EvaluationEvaluateSnapshotRequest("snap-1", "DISCHARGE"));

        assertThat(response.resultCount()).isEqualTo(1);
        ArgumentCaptor<EvaluationResult> result = ArgumentCaptor.forClass(EvaluationResult.class);
        verify(results).save(result.capture());
        assertThat(result.getValue().indicatorId()).isEqualTo("ei-runtime");
        verify(indicators, never()).findByIndicatorIdAndTenantId("ei-outside", "tenant-A");
    }

    private AssetVersion assetVersion(String versionId, String versionNo, AssetVersionStatus status) {
        Instant now = Instant.now();
        return new AssetVersion(
            null, versionId, "tenant-A", VersionedAssetType.EVALUATION,
            "IND.VTE.PROPHYLAXIS", versionNo, "全院", "MEDICAL_RECORD:DISCHARGE+24H",
            "0".repeat(64), AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            status, "version:" + versionId, "guideline-1", null, null,
            now, "qa-1", now, "qa-1", "trace-eval"
        );
    }

    private void authenticate(RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "qa-1",
                "n/a",
                List.of(new SimpleGrantedAuthority(role.authority()))
            )
        );
    }

    private ContextSnapshot snapshot(String snapshotId) {
        return new ContextSnapshot(
            null, snapshotId, "tenant-A", "dept-1", null, null, "runtime-release-test",
            "patient-1", "enc-1", com.medkernel.engine.context.ContextSnapshotStatus.ACTIVE,
            "[]", "{}",
            "{}", com.medkernel.engine.context.QualityStatus.VALID, "trace-eval",
            "sig", Instant.now(), "qa-1");
    }

    private CanonicalResource patientResource(String resourceId, String payload) {
        return new CanonicalResource(
            null, resourceId, "snap-1", "tenant-A", com.medkernel.engine.context.CanonicalResourceType.PATIENT,
            payload, null, null, null, null, Instant.now(),
            com.medkernel.engine.context.QualityStatus.VALID, 0, "trace-eval");
    }

    private String ruleDefinition(String fact, String operator, String valueLiteral) {
        return """
            {"all":[{"fact":"%s","operator":"%s","value":%s}]}
            """.formatted(fact, operator, valueLiteral);
    }

    private RuleDslEvaluation ruleEvaluation(boolean hit, String summary, String fact, boolean matched) {
        var explanation = json.createObjectNode();
        explanation.put("summary", summary);
        var evidence = json.createArrayNode();
        var item = json.createObjectNode();
        item.put("fact", fact);
        item.put("sourcePath", "$." + fact);
        item.put("operator", "equals");
        item.put("matched", matched);
        item.put("missing", !matched);
        evidence.add(item);
        explanation.set("conditionEvidence", evidence);
        return new RuleDslEvaluation(hit, hit ? RuleRiskLevel.MEDIUM : null, List.of(), explanation);
    }
}
