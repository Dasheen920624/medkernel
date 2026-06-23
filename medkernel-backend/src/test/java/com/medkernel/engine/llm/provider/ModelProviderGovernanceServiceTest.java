package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.medkernel.engine.llm.eval.ModelEvalService;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 模型 provider 治理服务单元测试（LLM-08 T13 双形态门禁 ENG-LLM-009 + LLM-07 T17 上线评测门禁 ENG-LLM-008）。
 */
class ModelProviderGovernanceServiceTest {

    private static final String CAPABILITY = "rule.draft";

    private ModelProviderConfigRepository repo;
    private DeploymentFormService deploymentForm;
    private ModelEvalService evalService;
    private ModelProviderRegistry registry;
    private ModelProviderCredentialRepository credentialRepo;
    private ProviderCredentialCodec credentialCodec;
    private AuditRecorder auditRecorder;
    private HighRiskChangeGuard highRiskGuard;
    private ModelProviderGovernanceService service;

    @BeforeEach
    void setUp() {
        repo = mock(ModelProviderConfigRepository.class);
        deploymentForm = mock(DeploymentFormService.class);
        evalService = mock(ModelEvalService.class);
        registry = mock(ModelProviderRegistry.class);
        credentialRepo = mock(ModelProviderCredentialRepository.class);
        credentialCodec = mock(ProviderCredentialCodec.class);
        auditRecorder = mock(AuditRecorder.class);
        highRiskGuard = mock(HighRiskChangeGuard.class);
        service = new ModelProviderGovernanceService(
            repo, deploymentForm, evalService, registry, credentialRepo, credentialCodec,
            auditRecorder, highRiskGuard);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "ops-001"));
        when(repo.save(any(ModelProviderConfig.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void newProviderIsAlwaysSavedDisabledAndDoesNotConsultEvaluationGate() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local")).thenReturn(Optional.empty());

        ModelProviderConfig saved = service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest("OLLAMA", "http://127.0.0.1:11434", "qwen2.5:7b", null));

        assertThat(saved.tenantId()).isEqualTo("tenant-1");
        assertThat(saved.providerType()).isEqualTo("OLLAMA");
        assertThat(saved.enabled()).isFalse();
        assertThat(saved.status()).isEqualTo("NOT_CONNECTED");
        verify(evalService, never()).isClearedForGoLive(any(), any(), any(), any());
        verify(deploymentForm, never()).allowsExternalProvider();
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_llm_provider", "ollama-local",
            "保存模型 provider ollama-local");
    }

    @Test
    void existingProviderUpdateRequiresMatchingVersionAndForcesDisabled() {
        ModelProviderConfig current = provider("Y", "HEALTHY", 4L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(current));

        ModelProviderConfig saved = service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest(
                "OLLAMA", "http://127.0.0.1:11434", "qwen2.5:7b", 4L));

        assertThat(saved.enabled()).isFalse();
        assertThat(saved.status()).isEqualTo("HEALTHY");
        assertThat(saved.version()).isEqualTo(4L);
    }

    @Test
    void existingProviderUpdateWithoutExpectedVersionIsRejected() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(provider("N", "HEALTHY", 4L)));

        assertConflict(() -> service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest(
                "OLLAMA", "http://127.0.0.1:11434", "qwen2.5:7b", null)));
        verify(repo, never()).save(any());
    }

    @Test
    void existingProviderUpdateWithStaleVersionIsRejected() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(provider("N", "HEALTHY", 4L)));

        assertConflict(() -> service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest(
                "OLLAMA", "http://127.0.0.1:11434", "qwen2.5:7b", 3L)));
        verify(repo, never()).save(any());
    }

    @Test
    void newProviderWithExpectedVersionIsRejected() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local")).thenReturn(Optional.empty());

        assertConflict(() -> service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest(
                "OLLAMA", "http://127.0.0.1:11434", "qwen2.5:7b", 0L)));
        verify(repo, never()).save(any());
    }

    @Test
    void endpointMustBeCleanHttpAbsoluteUrl() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local")).thenReturn(Optional.empty());

        for (String endpoint : new String[] {
            "https://user:secret@example.com",
            "https://example.com/v1?token=secret",
            "https://example.com/v1#fragment",
            "ftp://example.com/model",
            "/relative/model"
        }) {
            assertBadRequest(() -> service.upsertProvider("ollama-local",
                new ModelProviderUpsertRequest("OLLAMA", endpoint, "qwen2.5:7b", null)));
        }
        verify(repo, never()).save(any());
    }

    @Test
    void externalProviderCanBeRegisteredBeforeCredentialIsConfigured() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "claude-prod")).thenReturn(Optional.empty());

        ModelProviderConfig saved = service.upsertProvider("claude-prod",
            new ModelProviderUpsertRequest(
                "CLAUDE", "https://api.anthropic.com", "claude-opus-4-8", null));

        assertThat(saved.enabled()).isFalse();
        assertThat(saved.status()).isEqualTo("NOT_CONNECTED");
    }

    @Test
    void externalProviderRejectsPlainHttpEndpoint() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "claude-prod")).thenReturn(Optional.empty());

        assertBadRequest(() -> service.upsertProvider("claude-prod",
            new ModelProviderUpsertRequest(
                "CLAUDE", "http://api.anthropic.com", "claude-opus-4-8", null)));

        verify(repo, never()).save(any());
    }

    @Test
    void providerEndpointRemovesTrailingSlash() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local")).thenReturn(Optional.empty());

        ModelProviderConfig saved = service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest(
                "OLLAMA", "http://127.0.0.1:11434/", "qwen2.5:7b", null));

        assertThat(saved.endpointUri()).isEqualTo("http://127.0.0.1:11434");
    }

    @Test
    void getProviderReturnsCredentialPresenceWithoutCredentialReference() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(externalProviderConfig(7L)));
        when(credentialRepo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(credential(9L, "ciphertext", "fingerprint", "7xyz", 3L)));

        ModelProviderGovernanceView view = service.getProvider("external");

        assertThat(view.credentialConfigured()).isTrue();
        assertThat(view.credentialSource()).isEqualTo("VAULT");
        assertThat(view.version()).isEqualTo(7L);
        assertThat(view.toString()).doesNotContain("ciphertext", "fingerprint");
    }

    @Test
    void listProvidersUsesTenantScopedServerPaginationAndSanitizedCredentialMetadata() {
        PageRequest page = new PageRequest(2, 20, null);
        ModelProviderConfig config = externalProviderConfig(7L);
        ModelProviderCredential credential = credential(9L, "ciphertext", "fingerprint", "7xyz", 3L);
        when(repo.countByTenantId("tenant-1")).thenReturn(21L);
        when(repo.pageByTenantId("tenant-1", 20, 20)).thenReturn(List.of(config));
        when(credentialRepo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(credential));

        PageResponse<ModelProviderGovernanceView> result = service.listProviders(page);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(21);
        assertThat(result.items()).singleElement().satisfies(view -> {
            assertThat(view.credentialConfigured()).isTrue();
            assertThat(view.credentialSource()).isEqualTo("VAULT");
            assertThat(view.credentialLast4()).isEqualTo("7xyz");
            assertThat(view.credentialVersion()).isEqualTo(3L);
            assertThat(view.toString()).doesNotContain("ciphertext", "fingerprint");
        });
    }

    @Test
    void savingCredentialRequiresConfirmationBeforeMfaOrRepositoryAccess() {
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.saveCredential(
            "external",
            new ModelProviderCredentialUpsertRequest(
                "sk-fake-key-1234", "轮换生产模型凭据", null, false)));

        verify(highRiskGuard, never()).assertHighRiskAllowed(any(), any());
        verify(repo, never()).findByTenantIdAndProviderCode(any(), any());
        verify(credentialRepo, never()).findByTenantIdAndProviderCode(any(), any());
    }

    @Test
    void credentialChangeRequiresSubstantiveReasonEvenWhenApiValidationIsBypassed() {
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.saveCredential(
            "external",
            new ModelProviderCredentialUpsertRequest(
                "sk-fake-key-1234", "轮换", null, true)));
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.removeCredential(
            "external",
            new ModelProviderCredentialRemovalRequest("撤销", 3L, true)));

        verify(highRiskGuard, never()).assertHighRiskAllowed(any(), any());
        verify(repo, never()).findByTenantIdAndProviderCode(any(), any());
        verify(credentialRepo, never()).findByTenantIdAndProviderCode(any(), any());
    }

    @Test
    void savingNewCredentialEncryptsItAndForcesProviderDisabledAndDisconnected() {
        ModelProviderConfig current = externalProvider("Y", "HEALTHY", 5L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(current));
        when(credentialRepo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.empty());
        when(credentialCodec.encode("sk-fake-key-1234")).thenReturn(
            new ProviderCredentialCodec.EncodedCredential(
                "sm4:v1:cipher", "sha256-fingerprint", "1234"));
        when(credentialRepo.save(any(ModelProviderCredential.class))).thenAnswer(invocation ->
            withCredentialVersion(invocation.getArgument(0), 0L));

        ModelProviderGovernanceView view = service.saveCredential(
            "external",
            new ModelProviderCredentialUpsertRequest(
                "sk-fake-key-1234", "登记生产模型凭据", null, true));

        assertThat(view.enabled()).isFalse();
        assertThat(view.status()).isEqualTo("NOT_CONNECTED");
        assertThat(view.credentialConfigured()).isTrue();
        assertThat(view.credentialLast4()).isEqualTo("1234");
        assertThat(view.credentialVersion()).isZero();
        assertThat(view.toString())
            .doesNotContain("sk-fake-key-1234", "sm4:v1:cipher", "sha256-fingerprint");
        verify(highRiskGuard).assertHighRiskAllowed("model_provider_credential", "external");
        verify(auditRecorder).record(
            AuditAction.UPDATE,
            "mk_llm_provider_credential",
            "external",
            "保存模型 provider 凭据 external（尾标=1234，版本=0）：登记生产模型凭据");
    }

    @Test
    void rotatingCredentialRequiresCurrentCredentialVersion() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(externalProvider("N", "NOT_CONNECTED", 5L)));
        when(credentialRepo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(credential(9L, "old", "old-fingerprint", "0000", 3L)));

        assertConflict(() -> service.saveCredential(
            "external",
            new ModelProviderCredentialUpsertRequest(
                "sk-fake-key-5678", "轮换生产模型凭据", 2L, true)));

        verify(credentialCodec, never()).encode(any());
        verify(credentialRepo, never()).save(any());
    }

    @Test
    void removingCredentialForcesDisconnect() {
        ModelProviderConfig current = externalProvider("Y", "HEALTHY", 5L);
        ModelProviderCredential credential = credential(9L, "cipher", "fingerprint", "1234", 3L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(current));
        when(credentialRepo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(credential));

        ModelProviderGovernanceView view = service.removeCredential(
            "external",
            new ModelProviderCredentialRemovalRequest("撤销失效的生产模型凭据", 3L, true));

        assertThat(view.credentialConfigured()).isFalse();
        assertThat(view.credentialSource()).isEqualTo("NONE");
        assertThat(view.enabled()).isFalse();
        assertThat(view.status()).isEqualTo("NOT_CONNECTED");
        verify(credentialRepo).delete(credential);
        verify(repo).save(org.mockito.ArgumentMatchers.argThat(saved ->
            !saved.enabled()
                && "NOT_CONNECTED".equals(saved.status())));
        verify(auditRecorder).record(
            AuditAction.DELETE,
            "mk_llm_provider_credential",
            "external",
            "移除模型 provider 凭据 external（原尾标=1234，版本=3）：撤销失效的生产模型凭据");
    }

    @Test
    void enableRequiresHealthyCurrentVersionPassedEvaluationAndMfa() {
        ModelProviderConfig current = externalProvider("N", "HEALTHY", 5L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(current));
        when(deploymentForm.allowsExternalProvider()).thenReturn(true);
        when(evalService.isClearedForGoLive(
            "tenant-1", "external", current.modelVersion(), CAPABILITY)).thenReturn(true);

        ModelProviderGovernanceView enabled = service.enableProvider(
            "external",
            new ModelProviderActivationRequest(
                CAPABILITY,
                "医学回归评测通过并已核查，受控启用",
                5L,
                true));

        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.status()).isEqualTo("HEALTHY");
        verify(highRiskGuard).assertHighRiskAllowed("model_provider", "external");
        verify(auditRecorder).record(
            AuditAction.UPDATE,
            "mk_llm_provider",
            "external",
            "启用模型 provider external（capability=rule.draft）：医学回归评测通过并已核查，受控启用");
    }

    @Test
    void activationRequiresExplicitConfirmationAndReasonBeforeMfaOrRepositoryAccess() {
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.enableProvider(
            "ollama-local", new ModelProviderActivationRequest(CAPABILITY, "启用", 5L, false)));
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.enableProvider(
            "ollama-local", new ModelProviderActivationRequest(CAPABILITY, " ", 5L, true)));
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.enableProvider(
            "ollama-local", new ModelProviderActivationRequest(CAPABILITY, "启用", 5L, true)));
        assertError(ErrorCode.VALIDATION_FAILED, () -> service.enableProvider(
            "ollama-local", new ModelProviderActivationRequest(null, "受控启用模型服务", 5L, true)));

        verify(highRiskGuard, never()).assertHighRiskAllowed(any(), any());
        verify(repo, never()).findByTenantIdAndProviderCode(any(), any());
    }

    @Test
    void activationPropagatesMfaRejectionBeforeReadingProvider() {
        doThrow(new ApiException(ErrorCode.ENG_AUTH_010))
            .when(highRiskGuard).assertHighRiskAllowed("model_provider", "ollama-local");

        assertError(ErrorCode.ENG_AUTH_010, () -> service.enableProvider(
            "ollama-local",
            new ModelProviderActivationRequest(CAPABILITY, "受控启用模型服务", 5L, true)));

        verify(repo, never()).findByTenantIdAndProviderCode(any(), any());
    }

    @Test
    void activationRequiresCurrentExpectedVersion() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(provider("N", "HEALTHY", 5L)));

        assertConflict(() -> service.enableProvider(
            "ollama-local",
            new ModelProviderActivationRequest(CAPABILITY, "受控启用模型服务", null, true)));
        assertConflict(() -> service.enableProvider(
            "ollama-local",
            new ModelProviderActivationRequest(CAPABILITY, "受控启用模型服务", 4L, true)));

        verify(repo, never()).save(any());
    }

    @Test
    void unhealthyProviderCannotBeEnabled() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(provider("N", "NOT_CONNECTED", 5L)));

        assertConflict(() -> service.enableProvider(
            "ollama-local",
            new ModelProviderActivationRequest(CAPABILITY, "受控启用模型服务", 5L, true)));

        verify(evalService, never()).isClearedForGoLive(any(), any(), any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void externalProviderCannotBeEnabledInHospitalRuntime() {
        ModelProviderConfig current = externalProvider("N", "HEALTHY", 5L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(current));
        when(deploymentForm.allowsExternalProvider()).thenReturn(false);

        assertError(ErrorCode.ENG_LLM_009, () -> service.enableProvider(
            "external",
            new ModelProviderActivationRequest(CAPABILITY, "受控启用模型服务", 5L, true)));

        verify(evalService, never()).isClearedForGoLive(any(), any(), any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void providerWithoutPassedMedicalEvaluationCannotBeEnabled() {
        ModelProviderConfig current = externalProvider("N", "HEALTHY", 5L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(current));
        when(deploymentForm.allowsExternalProvider()).thenReturn(true);
        when(evalService.isClearedForGoLive(
            "tenant-1", "external", current.modelVersion(), CAPABILITY)).thenReturn(false);

        assertError(ErrorCode.ENG_LLM_008, () -> service.enableProvider(
            "external",
            new ModelProviderActivationRequest(CAPABILITY, "受控启用模型服务", 5L, true)));

        verify(repo, never()).save(any());
    }

    @Test
    void enablingAlreadyEnabledProviderAtCurrentVersionIsIdempotent() {
        ModelProviderConfig current = externalProvider("Y", "HEALTHY", 5L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(current));

        ModelProviderGovernanceView enabled = service.enableProvider(
            "external",
            new ModelProviderActivationRequest(CAPABILITY, "确认保持启用状态", 5L, true));

        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.version()).isEqualTo(5L);
        verify(repo, never()).save(any());
        verify(deploymentForm, never()).allowsExternalProvider();
        verify(evalService, never()).isClearedForGoLive(any(), any(), any(), any());
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void disableOnlyChangesEnabledFlagAndKeepsHealthState() {
        ModelProviderConfig current = externalProvider("Y", "HEALTHY", 5L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(current));

        ModelProviderGovernanceView disabled = service.disableProvider(
            "external", new ModelProviderActivationRequest(null, "维护窗口主动停用", 5L, true));

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.status()).isEqualTo("HEALTHY");
        assertThat(disabled.modelVersion()).isEqualTo(current.modelVersion());
        verify(deploymentForm, never()).allowsExternalProvider();
        verify(evalService, never()).isClearedForGoLive(any(), any(), any(), any());
        verify(auditRecorder).record(
            AuditAction.UPDATE,
            "mk_llm_provider",
            "external",
            "停用模型 provider external：维护窗口主动停用");
    }

    @Test
    void disablingAlreadyDisabledProviderAtCurrentVersionIsIdempotent() {
        ModelProviderConfig current = externalProvider("N", "HEALTHY", 5L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(current));

        ModelProviderGovernanceView disabled = service.disableProvider(
            "external", new ModelProviderActivationRequest(null, "确认保持停用状态", 5L, true));

        assertThat(disabled.enabled()).isFalse();
        verify(repo, never()).save(any());
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void disablingAlreadyDisabledProviderStillRejectsStaleVersion() {
        when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
            .thenReturn(Optional.of(externalProvider("N", "HEALTHY", 5L)));

        assertConflict(() -> service.disableProvider(
            "external", new ModelProviderActivationRequest(null, "确认保持停用状态", 4L, true)));

        verify(repo, never()).save(any());
    }

    @Test
    void healthCheckPersistsHealthyStatusAndAudits() {
        ModelProviderConfig configured = new ModelProviderConfig(
            1L, "tenant-1", "ollama-local", "OLLAMA", "http://127.0.0.1:11434",
            "qwen2.5:7b", "Y", "NOT_CONNECTED", null, "ops-001", null, "ops-001", 0L);
        ModelProvider adapter = mock(ModelProvider.class);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(configured));
        when(registry.resolveByCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(new ModelProviderRegistry.ResolvedProvider(adapter, configured)));
        when(adapter.checkHealth(configured)).thenReturn(ProviderHealth.HEALTHY);
        when(repo.save(any(ModelProviderConfig.class))).thenAnswer(invocation -> {
            ModelProviderConfig candidate = invocation.getArgument(0);
            return withVersion(candidate, candidate.version() + 1);
        });

        ModelProviderConfig checked = service.checkHealth("ollama-local");

        assertThat(checked.status()).isEqualTo("HEALTHY");
        assertThat(checked.enabled()).isTrue();
        assertThat(checked.version()).isEqualTo(1L);
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_llm_provider", "ollama-local",
            "探测模型 provider ollama-local status=HEALTHY");
    }

    @Test
    void changingConnectionMaterialResetsStaleHealthyStatus() {
        ModelProviderConfig existing = new ModelProviderConfig(
            1L, "tenant-1", "ollama-local", "OLLAMA", "http://127.0.0.1:11434",
            "qwen2.5:7b", "N", "HEALTHY", null, "ops-001", null, "ops-001", 0L);
        when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
            .thenReturn(Optional.of(existing));

        ModelProviderConfig saved = service.upsertProvider("ollama-local",
            new ModelProviderUpsertRequest(
                "OLLAMA", "http://127.0.0.1:22434", "qwen2.5:7b", 0L));

        assertThat(saved.status()).isEqualTo("NOT_CONNECTED");
    }

    private ModelProviderConfig provider(String enabledFlag, String status, Long version) {
        return new ModelProviderConfig(
            1L, "tenant-1", "ollama-local", "OLLAMA", "http://127.0.0.1:11434",
            "qwen2.5:7b", enabledFlag, status, null, "ops-001", null, "ops-001", version);
    }

    private ModelProviderConfig externalProviderConfig(Long version) {
        return new ModelProviderConfig(
            2L, "tenant-1", "external", "CLAUDE", "https://api.anthropic.com",
            "claude-opus-4-8", "N", "HEALTHY",
            null, "ops-001", null, "ops-001", version);
    }

    private ModelProviderConfig externalProvider(String enabledFlag, String status, Long version) {
        return new ModelProviderConfig(
            2L, "tenant-1", "external", "CLAUDE", "https://api.anthropic.com",
            "claude-opus-4-8", enabledFlag, status,
            null, "ops-001", null, "ops-001", version);
    }

    private ModelProviderConfig withVersion(ModelProviderConfig config, Long version) {
        return new ModelProviderConfig(
            config.id(), config.tenantId(), config.providerCode(), config.providerType(),
            config.endpointUri(), config.modelVersion(), config.enabledFlag(),
            config.status(), config.createdAt(), config.createdBy(), config.updatedAt(), config.updatedBy(), version);
    }

    private ModelProviderCredential credential(
            Long id,
            String ciphertext,
            String fingerprint,
            String last4,
            Long version) {
        Instant now = Instant.parse("2026-06-20T05:00:00Z");
        return new ModelProviderCredential(
            id, "tenant-1", "external", ciphertext, fingerprint, last4,
            now, "ops-001", now, "ops-001", "trace-1", version);
    }

    private ModelProviderCredential withCredentialVersion(
            ModelProviderCredential credential,
            Long version) {
        return new ModelProviderCredential(
            credential.id(), credential.tenantId(), credential.providerCode(),
            credential.credentialCiphertext(), credential.credentialFingerprint(),
            credential.credentialLast4(), credential.createdAt(), credential.createdBy(),
            credential.updatedAt(), credential.updatedBy(), credential.traceId(), version);
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.CONFLICT);
    }

    private void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    private void assertError(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(expected);
    }
}
