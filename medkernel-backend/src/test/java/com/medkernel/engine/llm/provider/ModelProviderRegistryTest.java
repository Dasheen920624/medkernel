package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * provider 注册表解析单元测试（LLM-08 FR-1/2/3 + 双形态门禁）。
 */
class ModelProviderRegistryTest {

    private ModelProviderConfigRepository repo;
    private DeploymentFormService deploymentForm;
    private ModelProvider ollama;
    private ModelProvider claude;
    private ModelProviderRegistry registry;

    @BeforeEach
    void setUp() {
        repo = mock(ModelProviderConfigRepository.class);
        deploymentForm = mock(DeploymentFormService.class);
        ollama = mock(ModelProvider.class);
        claude = mock(ModelProvider.class);
        when(ollama.type()).thenReturn(ProviderType.OLLAMA);
        when(claude.type()).thenReturn(ProviderType.CLAUDE);
        lenient().when(ollama.checkHealth(any())).thenReturn(ProviderHealth.HEALTHY);
        lenient().when(claude.checkHealth(any())).thenReturn(ProviderHealth.HEALTHY);
        registry = new ModelProviderRegistry(repo, deploymentForm, List.of(ollama, claude));
    }

    private ModelProviderConfig cfg(String code, String type) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new ModelProviderConfig(1L, "tenant-1", code, type,
            "http://x", type.equals("OLLAMA") ? null : "KEY", "v1", "Y", "HEALTHY", now, "s", now, "s");
    }

    @Test
    void localModelResolvesHealthyOllama() {
        when(repo.findByTenantIdAndEnabledFlag("tenant-1", "Y"))
            .thenReturn(List.of(cfg("ollama-local", "OLLAMA")));

        assertThat(registry.resolve("tenant-1", "LOCAL_MODEL"))
            .isPresent()
            .get()
            .satisfies(r -> assertThat(r.adapter().type()).isEqualTo(ProviderType.OLLAMA));
    }

    @Test
    void noProviderConfiguredResolvesEmpty() {
        when(repo.findByTenantIdAndEnabledFlag("tenant-1", "Y")).thenReturn(List.of());
        assertThat(registry.resolve("tenant-1", "LOCAL_MODEL")).isEmpty();
    }

    @Test
    void externalModelBlockedInHospitalRuntimeForm() {
        when(deploymentForm.allowsExternalProvider()).thenReturn(false);
        // 即便配了健康的外部 provider，运行侧内网形态也一律不解析（禁外部 provider）
        lenient().when(repo.findByTenantIdAndEnabledFlag("tenant-1", "Y"))
            .thenReturn(List.of(cfg("claude-prod", "CLAUDE")));

        assertThat(registry.resolve("tenant-1", "EXTERNAL_MODEL")).isEmpty();
    }

    @Test
    void externalModelResolvesInProductionCenter() {
        when(deploymentForm.allowsExternalProvider()).thenReturn(true);
        when(repo.findByTenantIdAndEnabledFlag("tenant-1", "Y"))
            .thenReturn(List.of(cfg("claude-prod", "CLAUDE")));

        assertThat(registry.resolve("tenant-1", "EXTERNAL_MODEL"))
            .isPresent()
            .get()
            .satisfies(r -> assertThat(r.adapter().type()).isEqualTo(ProviderType.CLAUDE));
    }

    @Test
    void unhealthyProviderIsSkipped() {
        when(repo.findByTenantIdAndEnabledFlag("tenant-1", "Y"))
            .thenReturn(List.of(cfg("ollama-local", "OLLAMA")));
        when(ollama.checkHealth(any())).thenReturn(ProviderHealth.NOT_CONNECTED);

        assertThat(registry.resolve("tenant-1", "LOCAL_MODEL")).isEmpty();
    }
}
