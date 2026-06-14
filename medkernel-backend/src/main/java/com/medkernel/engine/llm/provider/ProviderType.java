package com.medkernel.engine.llm.provider;

/**
 * 模型 provider 类型（LLM-08 · 核心 §11 B1/B2）。
 *
 * <p>{@link #OLLAMA} 为 B1 本地（内网可用）；其余为 B2 外部（仅外网生产中心可用，运行侧内网禁用）。
 */
public enum ProviderType {

    OLLAMA(false, "B1"),
    OPENAI_COMPATIBLE(true, "B2"),
    CLAUDE(true, "B2"),
    DIFY(true, "B2");

    private final boolean external;
    private final String modelMode;

    ProviderType(boolean external, String modelMode) {
        this.external = external;
        this.modelMode = modelMode;
    }

    public boolean external() {
        return external;
    }

    public String modelMode() {
        return modelMode;
    }
}
