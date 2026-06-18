package com.medkernel.engine.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 模型 provider 响应真实性字段校验，禁止用请求配置冒充实际返回版本。
 */
final class ProviderResponseValidator {

    private ProviderResponseValidator() {
    }

    static String requireModelVersion(JsonNode response, String providerCode) {
        JsonNode model = response == null ? null : response.get("model");
        if (model == null || !model.isTextual() || model.asText().isBlank()) {
            throw new ApiException(ErrorCode.ENG_LLM_002,
                "模型 provider 响应缺少真实 model 字段 code=" + providerCode);
        }
        return model.asText().trim();
    }

    static String requireContent(String content, String providerCode) {
        if (content == null || content.isBlank()) {
            throw new ApiException(ErrorCode.ENG_LLM_002,
                "模型 provider 响应缺少非空补全内容 code=" + providerCode);
        }
        return content;
    }
}
