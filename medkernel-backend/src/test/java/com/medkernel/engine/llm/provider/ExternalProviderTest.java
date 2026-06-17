package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

/**
 * B2 外部 provider 适配器单元测试（LLM-08）：OpenAI 兼容 + Claude。凭据经解析器，密钥不落库。
 */
class ExternalProviderTest {

    private ModelProviderHttpClient http;
    private ProviderCredentialResolver credentials;
    private OpenAiCompatibleProvider openai;
    private ClaudeProvider claude;

    @BeforeEach
    void setUp() {
        http = mock(ModelProviderHttpClient.class);
        credentials = mock(ProviderCredentialResolver.class);
        openai = new OpenAiCompatibleProvider(http, credentials);
        claude = new ClaudeProvider(http, credentials);
    }

    private ModelProviderConfig config(String type, String version) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new ModelProviderConfig(1L, "tenant-1", type.toLowerCase() + "-prod", type,
            "https://api.example.com", "MODEL_API_KEY", version, "Y", "HEALTHY", now, "system", now, "system");
    }

    @Test
    void providerTypesAreExternalB2() {
        assertThat(openai.type()).isEqualTo(ProviderType.OPENAI_COMPATIBLE);
        assertThat(claude.type()).isEqualTo(ProviderType.CLAUDE);
        assertThat(openai.type().external()).isTrue();
        assertThat(claude.type().modelMode()).isEqualTo("B2");
    }

    @Test
    void openAiParsesRealCompletion() {
        when(credentials.resolveSecret("MODEL_API_KEY")).thenReturn(Optional.of("sk-secret"));
        when(http.post(eq("https://api.example.com/v1/chat/completions"), any(), contains("gpt-4o"), eq(45_000)))
            .thenReturn("{\"model\":\"gpt-4o\",\"choices\":[{\"message\":{\"content\":\"候选内容\"}}]}");

        ProviderCompletion c = openai.complete(config("OPENAI_COMPATIBLE", "gpt-4o"),
            new ProviderRequest("knowledge.extract", "提取要素", 45_000));

        assertThat(c.content()).contains("候选内容");
        assertThat(c.modelVersion()).isEqualTo("gpt-4o");
    }

    @Test
    void claudeParsesRealCompletion() {
        when(credentials.resolveSecret("MODEL_API_KEY")).thenReturn(Optional.of("sk-ant"));
        when(http.post(eq("https://api.example.com/v1/messages"), any(), contains("claude-opus-4-8"), eq(45_000)))
            .thenReturn("{\"model\":\"claude-opus-4-8\",\"content\":[{\"type\":\"text\",\"text\":\"候选内容\"}]}");

        ProviderCompletion c = claude.complete(config("CLAUDE", "claude-opus-4-8"),
            new ProviderRequest("knowledge.extract", "提取要素", 45_000));

        assertThat(c.content()).contains("候选内容");
        assertThat(c.modelVersion()).isEqualTo("claude-opus-4-8");
    }

    @Test
    void missingCredentialIsNotConnectedAndCompleteFails() {
        when(credentials.resolveSecret("MODEL_API_KEY")).thenReturn(Optional.empty());

        assertThat(claude.checkHealth(config("CLAUDE", "claude-opus-4-8"))).isEqualTo(ProviderHealth.NOT_CONNECTED);
        assertThatThrownBy(() -> openai.complete(config("OPENAI_COMPATIBLE", "gpt-4o"),
                new ProviderRequest("knowledge.extract", "x", 45_000)))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void healthNotConnectedOnTransportError() {
        when(credentials.resolveSecret("MODEL_API_KEY")).thenReturn(Optional.of("sk"));
        when(http.get(anyString(), any(), eq(5_000))).thenThrow(new RuntimeException("timeout"));
        assertThat(openai.checkHealth(config("OPENAI_COMPATIBLE", "gpt-4o"))).isEqualTo(ProviderHealth.NOT_CONNECTED);
    }
}
