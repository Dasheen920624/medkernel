package com.medkernel.engine.llm;

/**
 * LLM-02 降级矩阵触发原因。
 *
 * <p>触发码会进入任务降级归因，需稳定可审计。
 */
public enum ModelFallbackTrigger {
    POLICY_BASELINE(false, "策略显式指定 B0 基线"),
    PROVIDER_UNAVAILABLE(true, "未找到可用模型服务"),
    EGRESS_BLOCKED(false, "外部模型外调治理阻断"),
    PROVIDER_TIMEOUT(true, "模型服务调用超时"),
    PROVIDER_RATE_LIMITED(true, "模型服务限流"),
    STRUCTURED_OUTPUT_FAILED(false, "模型输出结构化校验失败"),
    PROVIDER_DISCONNECTED(true, "模型服务断连或不可用"),
    PROVIDER_ERROR(true, "模型服务调用失败");

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
