package com.medkernel.engine.llm;

/**
 * LLM-02 降级矩阵触发原因。
 *
 * <p>触发码会进入任务降级归因，需稳定可审计。
 */
public enum ModelFallbackTrigger {
    POLICY_BASELINE(false, "策略显式指定 B0 基线"),
    PROVIDER_UNAVAILABLE(true, "未解析到可用模型 provider"),
    EGRESS_BLOCKED(false, "外部模型出域治理阻断"),
    PROVIDER_TIMEOUT(true, "模型 provider 超时"),
    PROVIDER_RATE_LIMITED(true, "模型 provider 限流"),
    STRUCTURED_OUTPUT_FAILED(false, "模型输出结构化校验失败"),
    PROVIDER_DISCONNECTED(true, "模型 provider 断连或不可用"),
    PROVIDER_ERROR(true, "模型 provider 调用失败");

    private final boolean retryable;
    private final String message;

    ModelFallbackTrigger(boolean retryable, String message) {
        this.retryable = retryable;
        this.message = message;
    }

    public boolean retryable() {
        return retryable;
    }

    public String message() {
        return message;
    }
}
