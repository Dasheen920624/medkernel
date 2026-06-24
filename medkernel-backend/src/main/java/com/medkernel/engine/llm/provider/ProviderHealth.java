package com.medkernel.engine.llm.provider;

/**
 * 模型服务连接状态（LLM-08 FR-2）。不可用一律 {@link #NOT_CONNECTED}，绝不伪装可用。
 */
public enum ProviderHealth {
    HEALTHY,
    NOT_CONNECTED
}
