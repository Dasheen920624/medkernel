package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Pageable;

import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.llm.provider.DeploymentFormService;
import com.medkernel.engine.llm.provider.ModelProviderRegistry;
import com.medkernel.engine.llm.provider.ProviderCompletion;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.RuntimeProperties;

/**
 * 医学回归评测服务单元测试（LLM-07 T16/T17/T18 编排）。
 */
class ModelEvalServiceTest {

    private MedicalRegressionCaseRepository caseRepo;
    private ModelEvalRunRepository runRepo;
    private ModelEvalCaseEvidenceRepository evidenceRepo;
    private MedicalRegressionEvaluator evaluator;
    private ModelProviderRegistry registry;
    private AuditRecorder auditRecorder;
    private HighRiskChangeGuard highRiskChangeGuard;
    private RuntimeProperties runtimeProperties;
    private DeploymentFormService deploymentFormService;
    private ModelEvalService service;

    @BeforeEach
    void setUp() {
        caseRepo = mock(MedicalRegressionCaseRepository.class);
        runRepo = mock(ModelEvalRunRepository.class);
        evidenceRepo = mock(ModelEvalCaseEvidenceRepository.class);
        evaluator = mock(MedicalRegressionEvaluator.class);
        registry = mock(ModelProviderRegistry.class);
        auditRecorder = mock(AuditRecorder.class);
        highRiskChangeGuard = mock(HighRiskChangeGuard.class);
        runtimeProperties = new RuntimeProperties();
        runtimeProperties.setReleaseFingerprint("release-current");
        deploymentFormService = mock(DeploymentFormService.class);
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.HOSPITAL_RUNTIME);
        service = new ModelEvalService(
            caseRepo, runRepo, evidenceRepo, evaluator, registry, auditRecorder, highRiskChangeGuard,
            runtimeProperties, deploymentFormService);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "quality-001"));
        authenticateAs("quality-001", "ROLE_QUALITY_GOVERNOR");
        when(runRepo.save(any(ModelEvalRun.class))).thenAnswer(invocation -> {
            ModelEvalRun run = invocation.getArgument(0);
            return new ModelEvalRun(
                run.id() == null ? 77L : run.id(), run.tenantId(), run.providerCode(), run.modelVersion(),
                run.capabilityCode(), run.promptVersion(), run.toolVersion(), run.releaseFingerprint(),
                run.totalCases(),
                run.passedCases(), run.failedCases(), run.qualityScore(), run.terminologyScore(),
                run.fakeCitationDetected(), run.redLineBreach(), run.hallucinationDetected(), run.status(),
                run.caseSummaryJson(), run.reviewComment(), run.reviewer(), run.signedAt(), run.createdAt(),
                run.createdBy(), run.updatedAt(), run.updatedBy());
        });
        when(runRepo.signOffPending(any(), anyString(), anyString(), anyString(), any(Instant.class)))
            .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    private MedicalRegressionCase aCase() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new MedicalRegressionCase(1L, "tenant-1", "rule.draft", "general", "输入", "期望",
            "[]", "[]", 100, null, "source-version:1", "N", "v1", "Y", now, "s", now, "s");
    }

    @Test
    void runEvaluation_noCases_recordsFailedRunWithoutCertifying() {
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of());

        ModelEvalRun run = service.runEvaluation("ollama-local", "qwen2.5:7b", "rule.draft");

        // 无基准集不得自动认证，必须 FAILED
        assertThat(run.status()).isEqualTo("FAILED");
    }

    @Test
    void runEvaluation_persistsEvaluatorVerdict() {
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of(aCase()));
        var adapter = mock(com.medkernel.engine.llm.provider.ModelProvider.class);
        var config = mock(com.medkernel.engine.llm.provider.ModelProviderConfig.class);
        when(config.modelVersion()).thenReturn("qwen2.5:7b");
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, config)));
        when(evaluator.evaluate(any(), any()))
            .thenReturn(new MedicalRegressionEvaluator.EvalVerdict(1, 1, 0, false, false, "PASSED"));

        ModelEvalRun run = service.runEvaluation("ollama-local", "qwen2.5:7b", "rule.draft");

        assertThat(run.status()).isEqualTo("PASSED");
        assertThat(run.releaseFingerprint()).isEqualTo("release-current");
        assertThat(run.caseSummaryJson()).contains("baselineFingerprint", "caseCount");
        verify(runRepo).save(argThat(r -> "PASSED".equals(r.status())
            && "ollama-local".equals(r.providerCode())
            && "rule.draft".equals(r.capabilityCode())
            && RegressionBaselineEvidence.matches(r.caseSummaryJson(), List.of(aCase()))
            && r.passedCases() == 1));
    }

    @Test
    void runEvaluationPersistsReviewableCaseEvidenceInSameWorkflow() {
        MedicalRegressionCase regCase = aCase();
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of(regCase));
        var adapter = mock(com.medkernel.engine.llm.provider.ModelProvider.class);
        var config = mock(com.medkernel.engine.llm.provider.ModelProviderConfig.class);
        when(config.modelVersion()).thenReturn("qwen2.5:7b");
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, config)));
        var caseEvidence = new MedicalRegressionEvaluator.EvalCaseEvidence(
            1L, "v1", "输入", "期望", null, "source-version:1",
            "期望", "[]", true, false, true, false, false, true, List.of());
        when(evaluator.evaluate(any(), any())).thenReturn(new MedicalRegressionEvaluator.EvalVerdict(
            1, 1, 0, false, false, "PASSED", List.of(caseEvidence)));
        when(runRepo.save(any(ModelEvalRun.class))).thenAnswer(invocation -> {
            ModelEvalRun run = invocation.getArgument(0);
            return new ModelEvalRun(
                77L, run.tenantId(), run.providerCode(), run.modelVersion(), run.capabilityCode(),
                run.promptVersion(), run.toolVersion(), run.releaseFingerprint(),
                run.totalCases(), run.passedCases(), run.failedCases(),
                run.qualityScore(), run.terminologyScore(), run.fakeCitationDetected(), run.redLineBreach(),
                run.hallucinationDetected(), run.status(), run.caseSummaryJson(), run.reviewComment(),
                run.reviewer(), run.signedAt(), run.createdAt(), run.createdBy(), run.updatedAt(), run.updatedBy());
        });

        service.runEvaluation("ollama-local", "qwen2.5:7b", "rule.draft");

        verify(evidenceRepo).saveAll(argThat(items -> {
            ModelEvalCaseEvidence evidence = items.iterator().next();
            return evidence.runId().equals(77L)
                && evidence.regressionCaseId().equals(1L)
                && "期望".equals(evidence.outputContent())
                && evidence.passed();
        }));
    }

    @Test
    void runEvaluation_rejectsModelVersionDifferentFromProviderConfiguration() {
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of(aCase()));
        var adapter = mock(com.medkernel.engine.llm.provider.ModelProvider.class);
        var config = mock(com.medkernel.engine.llm.provider.ModelProviderConfig.class);
        when(config.modelVersion()).thenReturn("qwen2.5:0.5b");
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, config)));

        assertThatThrownBy(() -> service.runEvaluation(
                "ollama-local", "qwen2.5:7b", "rule.draft"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("模型版本");

        verify(runRepo, never()).save(any(ModelEvalRun.class));
    }

    @Test
    void runEvaluation_failsWhenProviderRespondsWithDifferentModelVersion() {
        service = new ModelEvalService(
            caseRepo, runRepo, evidenceRepo, new MedicalRegressionEvaluator(), registry,
            auditRecorder, highRiskChangeGuard, runtimeProperties, deploymentFormService);
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of(aCase()));
        var adapter = mock(com.medkernel.engine.llm.provider.ModelProvider.class);
        var config = mock(com.medkernel.engine.llm.provider.ModelProviderConfig.class);
        when(config.modelVersion()).thenReturn("qwen2.5:0.5b");
        when(adapter.complete(any(), any()))
            .thenReturn(new ProviderCompletion("期望", "other-model", null, "[]"));
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, config)));

        ModelEvalRun run = service.runEvaluation(
            "ollama-local", "qwen2.5:0.5b", "rule.draft");

        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(run.failedCases()).isEqualTo(1);
    }

    @Test
    void runEvaluationAcceptsExactRegisteredCitationEmittedInProviderContent() {
        service = new ModelEvalService(
            caseRepo, runRepo, evidenceRepo, new MedicalRegressionEvaluator(), registry,
            auditRecorder, highRiskChangeGuard, runtimeProperties, deploymentFormService);
        MedicalRegressionCase citedCase = citedCase();
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of(citedCase));
        var adapter = mock(com.medkernel.engine.llm.provider.ModelProvider.class);
        var config = mock(com.medkernel.engine.llm.provider.ModelProviderConfig.class);
        when(config.modelVersion()).thenReturn("qwen2.5:0.5b");
        when(adapter.complete(any(), any())).thenReturn(new ProviderCompletion(
            "期望；证据引用 source-version:1", "qwen2.5:0.5b", null, "[]"));
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, config)));

        ModelEvalRun run = service.runEvaluation(
            "ollama-local", "qwen2.5:0.5b", "rule.draft");

        assertThat(run.status()).isEqualTo("PASSED");
        assertThat(run.fakeCitationDetected()).isEqualTo("N");
    }

    @Test
    void runEvaluationRejectsDifferentCitationEmittedInProviderContent() {
        service = new ModelEvalService(
            caseRepo, runRepo, evidenceRepo, new MedicalRegressionEvaluator(), registry,
            auditRecorder, highRiskChangeGuard, runtimeProperties, deploymentFormService);
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag("tenant-1", "rule.draft", "Y"))
            .thenReturn(List.of(citedCase()));
        var adapter = mock(com.medkernel.engine.llm.provider.ModelProvider.class);
        var config = mock(com.medkernel.engine.llm.provider.ModelProviderConfig.class);
        when(config.modelVersion()).thenReturn("qwen2.5:0.5b");
        when(adapter.complete(any(), any())).thenReturn(new ProviderCompletion(
            "期望；证据引用 source-version:999", "qwen2.5:0.5b", null, "[]"));
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, config)));

        ModelEvalRun run = service.runEvaluation(
            "ollama-local", "qwen2.5:0.5b", "rule.draft");

        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(run.fakeCitationDetected()).isEqualTo("Y");
    }

    @Test
    void runQualityEvaluation_persistsHallucinationFailureAndVersionTriple() {
        service = new ModelEvalService(
            caseRepo, runRepo, evidenceRepo, new MedicalRegressionEvaluator(), registry,
            auditRecorder, highRiskChangeGuard, runtimeProperties, deploymentFormService);
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        MedicalRegressionCase regCase = new MedicalRegressionCase(
            7L, "tenant-1", "recommendation.draft", "terminology",
            "请输出推荐解释", "建议人工复核", "[\"慢性肾脏病\"]",
            "[\"虚构医保编码 ZZZ-2026\"]", 80, null,
            "source-version:77#term", "Y", "2026.06", "Y", now, "s", now, "s");
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(
                "tenant-1", "recommendation.draft", "Y"))
            .thenReturn(List.of(regCase));

        ModelEvalRun run = service.runQualityEvaluation(new AiQualityEvalRunRequest(
            "recommendation.draft",
            "b0-fixture",
            "B0-Deterministic-Baseline",
            "prompt:v1",
            "tool:v1",
            List.of(new AiQualityEvalCaseOutput(
                7L,
                "建议人工复核。慢性肾脏病。虚构医保编码 ZZZ-2026。",
                "B0-Deterministic-Baseline",
                0.88,
                "source-version:77#term"))));

        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(run.capabilityCode()).isEqualTo("recommendation.draft");
        assertThat(run.promptVersion()).isEqualTo("prompt:v1");
        assertThat(run.toolVersion()).isEqualTo("tool:v1");
        assertThat(run.hallucinationDetected()).isEqualTo("Y");
        assertThat(run.qualityScore()).isLessThan(80.0);
        assertThat(run.terminologyScore()).isEqualTo(100.0);
        assertThat(run.caseSummaryJson()).contains("HALLUCINATION_FORBIDDEN_ASSERTION");
    }

    @Test
    void runQualityEvaluation_rejectsMismatchedOfflineOutputModelVersion() {
        service = new ModelEvalService(
            caseRepo, runRepo, evidenceRepo, new MedicalRegressionEvaluator(), registry,
            auditRecorder, highRiskChangeGuard, runtimeProperties, deploymentFormService);

        assertThatThrownBy(() -> service.runQualityEvaluation(new AiQualityEvalRunRequest(
            "recommendation.draft",
            "b0-fixture",
            "B0-Deterministic-Baseline",
            "prompt:v1",
            "tool:v1",
            List.of(new AiQualityEvalCaseOutput(
                7L,
                "建议人工复核。",
                "other-model",
                0.88,
                "source-version:77#term")))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("model_version");
        verify(runRepo, never()).save(any(ModelEvalRun.class));
    }

    @Test
    void qualityTrend_returnsRecentQualityRunsForCapabilityAndModelVersion() {
        Instant newest = Instant.parse("2026-06-14T00:05:00Z");
        Instant older = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findTop20ByTenantIdAndCapabilityCodeAndModelVersionOrderByCreatedAtDesc(
                "tenant-1", "recommendation.draft", "B0-Deterministic-Baseline"))
            .thenReturn(List.of(
                trendRun(12L, newest, 96.0, 100.0, "N", "PASSED", "prompt:v2", "tool:v2"),
                trendRun(11L, older, 72.0, 80.0, "Y", "FAILED", "prompt:v1", "tool:v1")));

        AiQualityTrendResponse trend = service.qualityTrend(
            "recommendation.draft", "B0-Deterministic-Baseline");

        assertThat(trend.capabilityCode()).isEqualTo("recommendation.draft");
        assertThat(trend.modelVersion()).isEqualTo("B0-Deterministic-Baseline");
        assertThat(trend.points()).extracting(AiQualityTrendPoint::runId).containsExactly(12L, 11L);
        assertThat(trend.points().get(0).qualityScore()).isEqualTo(96.0);
        assertThat(trend.points().get(1).hallucinationDetected()).isTrue();
    }

    @Test
    void listRunsUsesCurrentTenantStatusAndServerPagination() {
        when(runRepo.findByTenantIdAndStatusOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("PENDING_REVIEW"),
                any(Pageable.class)))
            .thenReturn(List.of(pendingRun("quality-author")));
        when(runRepo.countByTenantIdAndStatus("tenant-1", "PENDING_REVIEW")).thenReturn(21L);

        var page = service.listRuns("PENDING_REVIEW", new PageRequest(2, 10, null));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.total()).isEqualTo(21);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.runId()).isEqualTo(9L);
            assertThat(item.status()).isEqualTo("PENDING_REVIEW");
            assertThat(item.providerCode()).isEqualTo("claude-prod");
        });
        verify(runRepo).findByTenantIdAndStatusOrderByCreatedAtDesc(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("PENDING_REVIEW"),
            argThat(pageable -> pageable.getPageNumber() == 1 && pageable.getPageSize() == 10));
    }

    @Test
    void getRunDetailReturnsCurrentReviewableEvidenceWithoutTenantField() {
        when(runRepo.findById(9L)).thenReturn(Optional.of(pendingRun("quality-author")));
        stubReviewableEvidence();

        ModelEvalRunDetailResponse detail = service.getRunDetail(9L);

        assertThat(detail.run().runId()).isEqualTo(9L);
        assertThat(detail.cases()).singleElement().satisfies(item -> {
            assertThat(item.regressionCaseId()).isEqualTo(1L);
            assertThat(item.outputContent()).isEqualTo("期望");
            assertThat(item.passed()).isTrue();
        });
        assertThat(detail.evidenceComplete()).isTrue();
        assertThat(detail.baselineCurrent()).isTrue();
        assertThat(detail.reviewable()).isTrue();
        assertThat(detail.reviewBlockReason()).isNull();
    }

    @Test
    void signOff_pendingReviewBecomesPassedWithReviewer() {
        when(runRepo.findById(9L)).thenReturn(Optional.of(pendingRun("quality-author")));
        stubReviewableEvidence();

        ModelEvalRun signed = service.signOff(9L, signOffRequest());

        assertThat(signed.status()).isEqualTo("PASSED");
        assertThat(signed.reviewer()).isEqualTo("quality-001");
        assertThat(signed.reviewComment()).isEqualTo("已核查逐用例输出、来源引用与红线结论。");
        assertThat(signed.signedAt()).isNotNull();
        verify(highRiskChangeGuard).assertHighRiskAllowed("model_eval_sign_off", "9");
        verify(runRepo).signOffPending(
            org.mockito.ArgumentMatchers.eq(9L),
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("quality-001"),
            org.mockito.ArgumentMatchers.eq("已核查逐用例输出、来源引用与红线结论。"),
            any(Instant.class));
        verify(runRepo, never()).save(any(ModelEvalRun.class));
    }

    @Test
    void signOffRejectsRunWithoutReviewableCaseEvidence() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        MedicalRegressionCase currentCase = aCase();
        when(runRepo.findById(9L)).thenReturn(Optional.of(new ModelEvalRun(9L, "tenant-1",
            "claude-prod", "claude-opus-4-8", "rule.draft", null, null, "release-current",
            1, 1, 0, null, null, "N", "N", "N", "PENDING_REVIEW",
            RegressionBaselineEvidence.toJson(List.of(currentCase)),
            null, null, null, now, "quality-author", now, "quality-author")));
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-1", "rule.draft", "Y")).thenReturn(List.of(currentCase));
        when(evidenceRepo.findByTenantIdAndRunIdOrderByIdAsc("tenant-1", 9L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.signOff(
                9L, new ModelEvalSignOffRequest(true, "已核查逐用例输出、来源引用与红线结论。")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("逐用例证据不完整");

        verify(highRiskChangeGuard, never()).assertHighRiskAllowed(anyString(), anyString());
    }

    @Test
    void signOff_sameActorAsEvaluatorRejected() {
        when(runRepo.findById(9L)).thenReturn(Optional.of(pendingRun("quality-001")));

        assertThatThrownBy(() -> service.signOff(9L, signOffRequest()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("签字人与评测执行人必须分离");

        verify(runRepo, never()).save(any(ModelEvalRun.class));
        verify(runRepo, never()).signOffPending(
            any(), anyString(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void signOff_historicalReleaseRejectedBeforeHighRiskConfirmation() {
        when(runRepo.findById(9L)).thenReturn(Optional.of(
            pendingRun("quality-author", "release-historical")));
        stubReviewableEvidence();

        assertThatThrownBy(() -> service.signOff(9L, signOffRequest()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("历史运行制品");

        verify(highRiskChangeGuard, never()).assertHighRiskAllowed(anyString(), anyString());
        verify(runRepo, never()).signOffPending(
            any(), anyString(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void signOff_productionCenterRejectsPlaceholderReleaseFingerprint() {
        runtimeProperties.setReleaseFingerprint("development");
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.PRODUCTION_CENTER);
        when(runRepo.findById(9L)).thenReturn(Optional.of(
            pendingRun("quality-author", "development")));

        assertThatThrownBy(() -> service.signOff(9L, signOffRequest()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("生产中心禁止使用占位运行制品指纹");

        verify(highRiskChangeGuard, never()).assertHighRiskAllowed(anyString(), anyString());
        verify(runRepo, never()).signOffPending(
            any(), anyString(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void signOff_withoutBoundMfaRejected() {
        when(runRepo.findById(9L)).thenReturn(Optional.of(pendingRun("quality-author")));
        stubReviewableEvidence();
        doThrow(new ApiException(com.medkernel.shared.api.error.ErrorCode.ENG_AUTH_010))
            .when(highRiskChangeGuard).assertHighRiskAllowed("model_eval_sign_off", "9");

        assertThatThrownBy(() -> service.signOff(9L, signOffRequest()))
            .isInstanceOf(ApiException.class);

        verify(runRepo, never()).save(any(ModelEvalRun.class));
        verify(runRepo, never()).signOffPending(
            any(), anyString(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void signOff_nonQualityGovernorRejectedAtServiceBoundary() {
        authenticateAs("platform-admin", "ROLE_PLATFORM_GOVERNANCE_ADMIN");
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "platform-admin"));
        when(runRepo.findById(9L)).thenReturn(Optional.of(pendingRun("quality-author")));

        assertThatThrownBy(() -> service.signOff(9L, signOffRequest()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("质量治理员");

        verify(highRiskChangeGuard, never()).assertHighRiskAllowed(anyString(), anyString());
        verify(runRepo, never()).save(any(ModelEvalRun.class));
        verify(runRepo, never()).signOffPending(
            any(), anyString(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void signOff_concurrentStateTransitionRejectedWithoutOverwritingReviewer() {
        when(runRepo.findById(9L)).thenReturn(Optional.of(pendingRun("quality-author")));
        stubReviewableEvidence();
        when(runRepo.signOffPending(any(), anyString(), anyString(), anyString(), any(Instant.class)))
            .thenReturn(0);

        assertThatThrownBy(() -> service.signOff(9L, signOffRequest()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已被其他请求处理");

        verify(runRepo, never()).save(any(ModelEvalRun.class));
    }

    @Test
    void signOff_nonPendingRejected() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findById(9L)).thenReturn(Optional.of(new ModelEvalRun(9L, "tenant-1",
            "claude-prod", "claude-opus-4-8", "rule.draft", "prompt:v1", "tool:v1",
            "release-current",
            5, 4, 1, null, null, "N", "N", "N", "FAILED", "[]",
            null, null, null, now, "s", now, "s")));

        assertThatThrownBy(() -> service.signOff(9L, signOffRequest())).isInstanceOf(ApiException.class);
    }

    @Test
    void isClearedForGoLive_trueWhenPassedRunExists() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        MedicalRegressionCase currentCase = aCase();
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-1", "rule.draft", "Y")).thenReturn(List.of(currentCase));
        when(runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                "tenant-1", "ollama-local", "qwen2.5:7b", "rule.draft", "PASSED"))
            .thenReturn(Optional.of(new ModelEvalRun(1L, "tenant-1", "ollama-local", "qwen2.5:7b",
                "rule.draft", "prompt:v1", "tool:v1", "release-current",
                1, 1, 0, null, null, "N", "N", "N", "PASSED",
                RegressionBaselineEvidence.toJson(List.of(currentCase)),
                "逐例证据已核查并确认可放行。", "quality-001", now, now, "s", now, "s")));
        when(evidenceRepo.findByTenantIdAndRunIdOrderByIdAsc("tenant-1", 1L))
            .thenReturn(List.of(passedEvidence(1L, currentCase)));

        assertThat(service.isClearedForGoLive(
            "tenant-1", "ollama-local", "qwen2.5:7b", "rule.draft")).isTrue();
        assertThat(service.isClearedForGoLive(
            "tenant-1", "ollama-local", "other-version", "rule.draft")).isFalse();
        assertThat(service.isClearedForGoLive(
            "tenant-1", "ollama-local", "qwen2.5:7b", "pathway.draft")).isFalse();
    }

    @Test
    void isClearedForGoLive_falseWhenPassedRunHasNoPerCaseEvidence() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        MedicalRegressionCase currentCase = aCase();
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-1", "rule.draft", "Y")).thenReturn(List.of(currentCase));
        when(runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                "tenant-1", "incomplete-provider", "incomplete-model", "rule.draft", "PASSED"))
            .thenReturn(Optional.of(new ModelEvalRun(2L, "tenant-1", "incomplete-provider", "incomplete-model",
                "rule.draft", null, null, "release-current",
                1, 1, 0, null, null, "N", "N", "N", "PASSED",
                RegressionBaselineEvidence.toJson(List.of(currentCase)),
                null, null, null, now, "s", now, "s")));
        when(evidenceRepo.findByTenantIdAndRunIdOrderByIdAsc("tenant-1", 2L)).thenReturn(List.of());

        assertThat(service.isClearedForGoLive(
            "tenant-1", "incomplete-provider", "incomplete-model", "rule.draft")).isFalse();
    }

    @Test
    void isClearedForGoLive_falseWhenPassedRunBelongsToHistoricalRelease() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        MedicalRegressionCase currentCase = aCase();
        when(runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                "tenant-1", "ollama-local", "qwen2.5:7b", "rule.draft", "PASSED"))
            .thenReturn(Optional.of(new ModelEvalRun(
                1L, "tenant-1", "ollama-local", "qwen2.5:7b", "rule.draft",
                "prompt:v1", "tool:v1", "release-historical",
                1, 1, 0, null, null, "N", "N", "N", "PASSED",
                RegressionBaselineEvidence.toJson(List.of(currentCase)),
                "逐例证据已核查并确认可放行。", "quality-001", now, now, "s", now, "s")));

        assertThat(service.isClearedForGoLive(
            "tenant-1", "ollama-local", "qwen2.5:7b", "rule.draft")).isFalse();
        verify(caseRepo, never()).findByTenantIdAndCapabilityCodeAndEnabledFlag(
            anyString(), anyString(), anyString());
    }

    @Test
    void getRunDetail_marksHistoricalReleaseAsNotReviewable() {
        when(runRepo.findById(9L)).thenReturn(Optional.of(
            pendingRun("quality-author", "release-historical")));
        stubReviewableEvidence();

        ModelEvalRunDetailResponse detail = service.getRunDetail(9L);

        assertThat(detail.releaseCurrent()).isFalse();
        assertThat(detail.reviewable()).isFalse();
        assertThat(detail.reviewBlockReason()).contains("历史运行制品");
    }

    @Test
    void isClearedForGoLive_falseWhenPassedRunUsesStaleBaseline() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        MedicalRegressionCase evaluatedCase = aCase();
        MedicalRegressionCase currentCase = new MedicalRegressionCase(
            evaluatedCase.id(), evaluatedCase.tenantId(), evaluatedCase.capabilityCode(),
            evaluatedCase.caseDomain(), evaluatedCase.caseInput(), "更新后的期望",
            evaluatedCase.expectedTermsJson(), evaluatedCase.forbiddenAssertionsJson(),
            evaluatedCase.minScore(), evaluatedCase.redLineType(), evaluatedCase.sourceReference(),
            evaluatedCase.citationRequired(), evaluatedCase.caseVersion(), evaluatedCase.enabledFlag(),
            evaluatedCase.createdAt(), evaluatedCase.createdBy(), evaluatedCase.updatedAt(), evaluatedCase.updatedBy());
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-1", "rule.draft", "Y")).thenReturn(List.of(currentCase));
        when(runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                "tenant-1", "ollama-local", "qwen2.5:7b", "rule.draft", "PASSED"))
            .thenReturn(Optional.of(new ModelEvalRun(1L, "tenant-1", "ollama-local", "qwen2.5:7b",
                "rule.draft", "prompt:v1", "tool:v1", "release-current",
                1, 1, 0, null, null, "N", "N", "N", "PASSED",
                RegressionBaselineEvidence.toJson(List.of(evaluatedCase)),
                "逐例证据已核查并确认可放行。", "quality-001", now, now, "s", now, "s")));

        assertThat(service.isClearedForGoLive(
            "tenant-1", "ollama-local", "qwen2.5:7b", "rule.draft")).isFalse();
    }

    private ModelEvalRun trendRun(
            Long id,
            Instant createdAt,
            double qualityScore,
            double terminologyScore,
            String hallucinationDetected,
            String status,
            String promptVersion,
            String toolVersion) {
        return new ModelEvalRun(
            id, "tenant-1", "b0-fixture", "B0-Deterministic-Baseline",
            "recommendation.draft", promptVersion, toolVersion, "release-current",
            3, "PASSED".equals(status) ? 3 : 2, "PASSED".equals(status) ? 0 : 1,
            qualityScore, terminologyScore, "N", "N", hallucinationDetected,
            status, "[]", null, null, null, createdAt, "s", createdAt, "s");
    }

    private MedicalRegressionCase citedCase() {
        MedicalRegressionCase base = aCase();
        return new MedicalRegressionCase(
            base.id(), base.tenantId(), base.capabilityCode(), base.caseDomain(),
            base.caseInput(), base.expectedPhrase(), base.expectedTermsJson(),
            base.forbiddenAssertionsJson(), base.minScore(), base.redLineType(),
            base.sourceReference(), "Y", base.caseVersion(), base.enabledFlag(),
            base.createdAt(), base.createdBy(), base.updatedAt(), base.updatedBy());
    }

    private ModelEvalRun pendingRun(String createdBy) {
        return pendingRun(createdBy, "release-current");
    }

    private ModelEvalRun pendingRun(String createdBy, String releaseFingerprint) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new ModelEvalRun(
            9L, "tenant-1", "claude-prod", "claude-opus-4-8", "rule.draft", null, null,
            releaseFingerprint,
            1, 1, 0, null, null, "N", "N", "N", "PENDING_REVIEW",
            RegressionBaselineEvidence.toJson(List.of(aCase())),
            null, null, null, now, createdBy, now, createdBy);
    }

    private void stubReviewableEvidence() {
        MedicalRegressionCase currentCase = aCase();
        when(caseRepo.findByTenantIdAndCapabilityCodeAndEnabledFlag(
            "tenant-1", "rule.draft", "Y")).thenReturn(List.of(currentCase));
        when(evidenceRepo.findByTenantIdAndRunIdOrderByIdAsc("tenant-1", 9L)).thenReturn(List.of(
            new ModelEvalCaseEvidence(
                1L, "tenant-1", 9L, currentCase.id(), currentCase.caseVersion(),
                currentCase.caseInput(), currentCase.expectedPhrase(), currentCase.redLineType(),
                currentCase.sourceReference(), "期望", "[]", "Y", "N", "Y", "N", "N", "Y",
                "[]", currentCase.createdAt(), "quality-author")));
    }

    private ModelEvalCaseEvidence passedEvidence(Long runId, MedicalRegressionCase currentCase) {
        return new ModelEvalCaseEvidence(
            1L, "tenant-1", runId, currentCase.id(), currentCase.caseVersion(),
            currentCase.caseInput(), currentCase.expectedPhrase(), currentCase.redLineType(),
            currentCase.sourceReference(), "期望", "[]", "Y", "N", "Y", "N", "N", "Y",
            "[]", currentCase.createdAt(), "quality-author");
    }

    private ModelEvalSignOffRequest signOffRequest() {
        return new ModelEvalSignOffRequest(true, "已核查逐用例输出、来源引用与红线结论。");
    }

    private void authenticateAs(String principal, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority(authority))));
    }
}
