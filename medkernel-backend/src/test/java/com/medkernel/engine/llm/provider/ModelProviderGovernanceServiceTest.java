package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.llm.eval.ModelEvalService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 模型 provider 治理服务单元测试（LLM-08 T13 双形态门禁 ENG-LLM-009 + LLM-07 T17 上线评测门禁 ENG-LLM-008）。
 */
class ModelProviderGovernanceServiceTest {

    private ModelProviderConfigRepository repo;
    private DeploymentFormService deploymentForm;
    private ModelEvalService evalService;
    private ModelProviderRegistry registry;
    private AuditRecorder auditRecorder;
    private ModelProviderGovernanceService service;

    @BeforeEach
    void setUp() {
        repo = mock(ModelProviderConfigRepository.class);
        deploymentForm = mock(DeploymentFormService.class);
        evalService = mock(ModelEvalService.class);
        registry = mock(ModelProviderRegistry.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ModelProviderGovernanceService(repo, deploymentForm, evalService, registry, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "ops-001"));
        when(repo.save(any(ModelProviderConfig.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void upsertLocalProviderPersistsAndAudits() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local")).thenReturn(Optional.empty());
        when(evalService.isClearedForGoLive("tenant-1", "ollama-local", "qwen2.5:7b")).thenReturn(true);

        ModelProviderConfig saved = service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest("OLLAMA", "http://127.0.0.1:11434", null, "qwen2.5:7b", true));

        assertThat(saved.tenantId()).isEqualTo("tenant-1");
        assertThat(saved.providerType()).isEqualTo("OLLAMA");
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_llm_provider", "ollama-local",
            "保存模型 provider ollama-local");
    }

    @Test
    void enablingExternalProviderInHospitalRuntimeIsRejectedWithEngLlm009() {
        when(deploymentForm.allowsExternalProvider()).thenReturn(false);

        assertThatThrownBy(() -> service.upsertProvider("claude-prod",
                new ModelProviderUpsertRequest("CLAUDE", "https://api.anthropic.com", "KEY", "claude-opus-4-8", true)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_009);
        verify(repo, never()).save(any());
    }

    @Test
    void enablingExternalProviderInProductionCenterPersists() {
        when(deploymentForm.allowsExternalProvider()).thenReturn(true);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "claude-prod")).thenReturn(Optional.empty());
        when(evalService.isClearedForGoLive("tenant-1", "claude-prod", "claude-opus-4-8")).thenReturn(true);

        ModelProviderConfig saved = service.upsertProvider("claude-prod",
            new ModelProviderUpsertRequest("CLAUDE", "https://api.anthropic.com", "KEY", "claude-opus-4-8", true));

        assertThat(saved.providerType()).isEqualTo("CLAUDE");
        assertThat(saved.enabled()).isTrue();
    }

    @Test
    void enablingProviderWithoutPassedEvaluationIsRejectedWithEngLlm008() {
        when(evalService.isClearedForGoLive("tenant-1", "ollama-local", "qwen2.5:7b")).thenReturn(false);

        assertThatThrownBy(() -> service.upsertProvider("ollama-local",
                new ModelProviderUpsertRequest("OLLAMA", "http://127.0.0.1:11434", null, "qwen2.5:7b", true)))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_008);
        verify(repo, never()).save(any());
    }

    @Test
    void registeringDisabledProviderSkipsEvaluationGate() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local")).thenReturn(Optional.empty());

        ModelProviderConfig saved = service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest("OLLAMA", "http://127.0.0.1:11434", null, "qwen2.5:7b", false));

        assertThat(saved.enabled()).isFalse();
        verify(evalService, never()).isClearedForGoLive(any(), any(), any());
    }

    @Test
    void healthCheckPersistsHealthyStatusAndAudits() {
        ModelProviderConfig configured = new ModelProviderConfig(
            1L, "tenant-1", "ollama-local", "OLLAMA", "http://127.0.0.1:11434",
            null, "qwen2.5:7b", "N", "NOT_CONNECTED", null, "ops-001", null, "ops-001");
        ModelProvider adapter = mock(ModelProvider.class);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(configured));
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, configured)));
        when(adapter.checkHealth(configured)).thenReturn(ProviderHealth.HEALTHY);

        ModelProviderConfig checked = service.checkHealth("ollama-local");

        assertThat(checked.status()).isEqualTo("HEALTHY");
        assertThat(checked.enabled()).isFalse();
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_llm_provider", "ollama-local",
            "探测模型 provider ollama-local status=HEALTHY");
    }

    @Test
    void changingConnectionMaterialResetsStaleHealthyStatus() {
        ModelProviderConfig existing = new ModelProviderConfig(
            1L, "tenant-1", "ollama-local", "OLLAMA", "http://127.0.0.1:11434",
            null, "qwen2.5:7b", "N", "HEALTHY", null, "ops-001", null, "ops-001");
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(existing));

        ModelProviderConfig saved = service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest(
                "OLLAMA", "http://127.0.0.1:22434", null, "qwen2.5:7b", false));

        assertThat(saved.status()).isEqualTo("NOT_CONNECTED");
    }
}
