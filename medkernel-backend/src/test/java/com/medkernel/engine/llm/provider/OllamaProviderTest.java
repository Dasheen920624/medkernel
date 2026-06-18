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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 本地 Ollama provider 适配器单元测试（LLM-08 B1）。HTTP 经接口注入 mock，不连真实外网。
 */
class OllamaProviderTest {

    private ModelProviderHttpClient http;
    private OllamaProvider provider;

    private ModelProviderConfig config() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new ModelProviderConfig(1L, "tenant-1", "ollama-local", "OLLAMA",
            "http://127.0.0.1:11434", null, "qwen2.5:7b", "Y", "HEALTHY", now, "system", now, "system");
    }

    @BeforeEach
    void setUp() {
        http = mock(ModelProviderHttpClient.class);
        provider = new OllamaProvider(http);
    }

    @Test
    void typeIsOllama() {
        assertThat(provider.type()).isEqualTo(ProviderType.OLLAMA);
    }

    @Test
    void completeParsesRealCompletionWithoutFabricatingConfidenceOrCitations() {
        when(http.post(eq("http://127.0.0.1:11434/api/generate"), any(), contains("qwen2.5:7b"), eq(30_000)))
            .thenReturn("{\"model\":\"qwen2.5:7b\",\"response\":\"候选：高血压病史\",\"done\":true}");

        ProviderCompletion completion = provider.complete(config(),
            new ProviderRequest("knowledge.extract", "提取病史要素", 30_000));

        assertThat(completion.content()).contains("候选：高血压病史");
        assertThat(completion.modelVersion()).isEqualTo("qwen2.5:7b");
        // 铁律 #1：本地补全无置信度/引文，绝不伪造
        assertThat(completion.confidence()).isNull();
        assertThat(completion.sourceCitations()).isEqualTo("[]");
    }

    @Test
    void completeRejectsResponseWithoutActualModelVersion() {
        when(http.post(eq("http://127.0.0.1:11434/api/generate"), any(), contains("qwen2.5:7b"), eq(30_000)))
            .thenReturn("{\"response\":\"候选内容\",\"done\":true}");

        assertThatThrownBy(() -> provider.complete(config(),
                new ProviderRequest("knowledge.extract", "提取病史要素", 30_000)))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_002);
    }

    @Test
    void completeRejectsEmptyContent() {
        when(http.post(eq("http://127.0.0.1:11434/api/generate"), any(), contains("qwen2.5:7b"), eq(30_000)))
            .thenReturn("{\"model\":\"qwen2.5:7b\",\"response\":\"  \",\"done\":true}");

        assertThatThrownBy(() -> provider.complete(config(),
                new ProviderRequest("knowledge.extract", "提取病史要素", 30_000)))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_002);
    }

    @Test
    void checkHealthHealthyWhenReachable() {
        when(http.get(anyString(), any(), eq(5_000))).thenReturn("{\"models\":[]}");
        assertThat(provider.checkHealth(config())).isEqualTo(ProviderHealth.HEALTHY);
    }

    @Test
    void checkHealthNotConnectedOnTransportError() {
        when(http.get(anyString(), any(), eq(5_000))).thenThrow(new RuntimeException("connection refused"));
        assertThat(provider.checkHealth(config())).isEqualTo(ProviderHealth.NOT_CONNECTED);
    }
}
