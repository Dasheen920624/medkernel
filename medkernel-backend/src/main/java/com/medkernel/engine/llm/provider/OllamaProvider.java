package com.medkernel.engine.llm.provider;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 本地 Ollama provider 适配器（LLM-08 B1，内网可用）。
 *
 * <p>调 {@code {endpoint}/api/generate} 真实补全：产出真实 {@code model} 版本，
 * 无置信度/引文则诚实置 {@code null}/{@code "[]"}（铁律 #1，绝不伪造）。
 */
@Component
public class OllamaProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int HEALTH_TIMEOUT_MS = 5_000;

    private final ModelProviderHttpClient http;

    public OllamaProvider(ModelProviderHttpClient http) {
        this.http = http;
    }

    @Override
    public ProviderType type() {
        return ProviderType.OLLAMA;
    }

    @Override
    public ProviderHealth checkHealth(ModelProviderConfig config) {
        try {
            http.get(baseUri(config) + "/api/tags", Map.of(), HEALTH_TIMEOUT_MS);
            return ProviderHealth.HEALTHY;
        } catch (RuntimeException unreachable) {
            log.warn("Ollama provider 探活失败 code={}：{}", config.providerCode(), unreachable.getMessage());
            return ProviderHealth.NOT_CONNECTED;
        }
    }

    @Override
    public ProviderCompletion complete(ModelProviderConfig config, ProviderRequest request) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", config.modelVersion());
        payload.put("prompt", request.prompt());
        payload.put("stream", false);

        String raw;
        try {
            raw = http.post(baseUri(config) + "/api/generate",
                Map.of("Content-Type", "application/json"), payload.toString(), request.timeoutMs());
        } catch (RuntimeException callFailed) {
            throw new ApiException(ErrorCode.ENG_LLM_003,
                "Ollama provider 调用失败 code=" + config.providerCode() + "：" + callFailed.getMessage());
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            String content = node.path("response").asText("");
            String modelVersion = node.path("model").asText(config.modelVersion());
            // Ollama 原生补全不返回置信度/来源引文，诚实置空，绝不伪造。
            return new ProviderCompletion(content, modelVersion, null, "[]");
        } catch (Exception parseFailed) {
            throw new ApiException(ErrorCode.ENG_LLM_002,
                "Ollama provider 返回无法解析 code=" + config.providerCode());
        }
    }

    private String baseUri(ModelProviderConfig config) {
        String uri = config.endpointUri();
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }
}
