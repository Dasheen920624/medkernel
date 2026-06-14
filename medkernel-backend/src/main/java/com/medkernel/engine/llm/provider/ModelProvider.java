package com.medkernel.engine.llm.provider;

/**
 * 模型 provider 适配器统一接口（LLM-08 FR-1）。
 *
 * <p>按 {@link ProviderType} 可插拔：B1 本地 Ollama / B2 外部 OpenAI 兼容·Claude / Dify。
 * 健康检查不可用标 {@link ProviderHealth#NOT_CONNECTED}；补全产出真实 {@code model_version}，绝不伪造。
 */
public interface ModelProvider {

    ProviderType type();

    ProviderHealth checkHealth(ModelProviderConfig config);

    ProviderCompletion complete(ModelProviderConfig config, ProviderRequest request);
}
