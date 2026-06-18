package com.medkernel.engine.llm.provider;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * OpenAI 兼容 API provider 适配器（LLM-08 B2，仅外网生产中心可用）。
 *
 * <p>调 {@code {endpoint}/v1/chat/completions}；密钥经 {@link ProviderCredentialResolver} 解析，缺失即不可用。
 */
@Component
public class OpenAiCompatibleProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int HEALTH_TIMEOUT_MS = 5_000;

    private final ModelProviderHttpClient http;
    private final ProviderCredentialResolver credentials;

    public OpenAiCompatibleProvider(ModelProviderHttpClient http, ProviderCredentialResolver credentials) {
        this.http = http;
        this.credentials = credentials;
    }

    @Override
    public ProviderType type() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public ProviderHealth checkHealth(ModelProviderConfig config) {
        Optional<String> secret = credentials.resolveSecret(config.credentialRef());
        if (secret.isEmpty()) {
            return ProviderHealth.NOT_CONNECTED;
        }
        try {
            http.get(baseUri(config) + "/v1/models",
                Map.of("Authorization", "Bearer " + secret.get()), HEALTH_TIMEOUT_MS);
            return ProviderHealth.HEALTHY;
        } catch (RuntimeException unreachable) {
            log.warn("OpenAI 兼容 provider 探活失败 code={}：{}", config.providerCode(), unreachable.getMessage());
            return ProviderHealth.NOT_CONNECTED;
        }
    }

    @Override
    public ProviderCompletion complete(ModelProviderConfig config, ProviderRequest request) {
        String secret = credentials.resolveSecret(config.credentialRef())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_LLM_003,
                "OpenAI 兼容 provider 凭据未配置 code=" + config.providerCode()));

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", config.modelVersion());
        ArrayNode messages = payload.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", request.prompt());

        String raw;
        try {
            raw = http.post(baseUri(config) + "/v1/chat/completions",
                Map.of("Content-Type", "application/json", "Authorization", "Bearer " + secret),
                payload.toString(),
                request.timeoutMs());
        } catch (RuntimeException callFailed) {
            throw new ApiException(ErrorCode.ENG_LLM_003,
                "OpenAI 兼容 provider 调用失败 code=" + config.providerCode() + "：" + callFailed.getMessage());
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw);
            String content = ProviderResponseValidator.requireContent(
                node.path("choices").path(0).path("message").path("content").asText(""),
                config.providerCode());
            String modelVersion = ProviderResponseValidator.requireModelVersion(node, config.providerCode());
            return new ProviderCompletion(content, modelVersion, null, "[]");
        } catch (ApiException protocolInvalid) {
            throw protocolInvalid;
        } catch (Exception parseFailed) {
            throw new ApiException(ErrorCode.ENG_LLM_002,
                "OpenAI 兼容 provider 返回无法解析 code=" + config.providerCode());
        }
    }

    private String baseUri(ModelProviderConfig config) {
        String uri = config.endpointUri();
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }
}
