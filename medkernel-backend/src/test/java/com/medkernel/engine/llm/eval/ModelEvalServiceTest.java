package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.llm.provider.ModelProviderRegistry;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 医学回归评测服务单元测试（LLM-07 T16/T17/T18 编排）。
 */
class ModelEvalServiceTest {

    private MedicalRegressionCaseRepository caseRepo;
    private ModelEvalRunRepository runRepo;
    private MedicalRegressionEvaluator evaluator;
    private ModelProviderRegistry registry;
    private AuditRecorder auditRecorder;
    private ModelEvalService service;

    @BeforeEach
    void setUp() {
        caseRepo = mock(MedicalRegressionCaseRepository.class);
        runRepo = mock(ModelEvalRunRepository.class);
        evaluator = mock(MedicalRegressionEvaluator.class);
        registry = mock(ModelProviderRegistry.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ModelEvalService(caseRepo, runRepo, evaluator, registry, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "quality-001"));
        when(runRepo.save(any(ModelEvalRun.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private MedicalRegressionCase aCase() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new MedicalRegressionCase(1L, "tenant-1", "rule.draft", "输入", "期望",
            null, "source-version:1", "N", "v1", "Y", now, "s", now, "s");
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
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, config)));
        when(evaluator.evaluate(any(), any()))
            .thenReturn(new MedicalRegressionEvaluator.EvalVerdict(1, 1, 0, false, false, "PASSED"));

        ModelEvalRun run = service.runEvaluation("ollama-local", "qwen2.5:7b", "rule.draft");

        assertThat(run.status()).isEqualTo("PASSED");
        verify(runRepo).save(argThat(r -> "PASSED".equals(r.status())
            && "ollama-local".equals(r.providerCode()) && r.passedCases() == 1));
    }

    @Test
    void signOff_pendingReviewBecomesPassedWithReviewer() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findById(9L)).thenReturn(Optional.of(new ModelEvalRun(9L, "tenant-1",
            "claude-prod", "claude-opus-4-8", 5, 5, 0, "N", "N", "PENDING_REVIEW",
            null, null, now, "s", now, "s")));

        ModelEvalRun signed = service.signOff(9L);

        assertThat(signed.status()).isEqualTo("PASSED");
        assertThat(signed.reviewer()).isEqualTo("quality-001");
        assertThat(signed.signedAt()).isNotNull();
    }

    @Test
    void signOff_nonPendingRejected() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findById(9L)).thenReturn(Optional.of(new ModelEvalRun(9L, "tenant-1",
            "claude-prod", "claude-opus-4-8", 5, 4, 1, "N", "N", "FAILED",
            null, null, now, "s", now, "s")));

        assertThatThrownBy(() -> service.signOff(9L)).isInstanceOf(ApiException.class);
    }

    @Test
    void isClearedForGoLive_trueWhenPassedRunExists() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(runRepo.findFirstByTenantIdAndProviderCodeAndModelVersionAndStatusOrderByIdDesc(
                "tenant-1", "ollama-local", "qwen2.5:7b", "PASSED"))
            .thenReturn(Optional.of(new ModelEvalRun(1L, "tenant-1", "ollama-local", "qwen2.5:7b",
                3, 3, 0, "N", "N", "PASSED", "quality-001", now, now, "s", now, "s")));

        assertThat(service.isClearedForGoLive("tenant-1", "ollama-local", "qwen2.5:7b")).isTrue();
        assertThat(service.isClearedForGoLive("tenant-1", "ollama-local", "other-version")).isFalse();
    }
}
