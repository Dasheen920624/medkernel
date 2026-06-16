package com.medkernel.engine.llm;

import java.util.Locale;

/**
 * LLM-02 B0/B1/B2 降级矩阵。
 *
 * <p>当前矩阵的安全策略是：B1/B2 任一运行期不可用或结构化失败均退回 B0；
 * B0 策略本身只记录显式基线归因，不伪造上级模型。
 */
public class ModelFallbackMatrix {

    public ModelFallbackDecision decide(String routeStrategy, ModelFallbackTrigger trigger, String detail) {
        String strategy = normalize(routeStrategy);
        ModelFallbackTrigger actualTrigger = trigger == null ? ModelFallbackTrigger.PROVIDER_ERROR : trigger;
        String sourceMode = sourceMode(strategy);
        String fallbackMode = "B0";
        boolean fallbackUsed = !"B0".equals(sourceMode) || actualTrigger != ModelFallbackTrigger.POLICY_BASELINE;
        String reason = "[LLM-02:" + actualTrigger.name() + "] " + sourceMode + " -> "
            + fallbackMode + "：" + actualTrigger.message();
        if (detail != null && !detail.isBlank()) {
            reason += "；" + detail.trim();
        }
        return new ModelFallbackDecision(
            strategy,
            sourceMode,
            fallbackMode,
            fallbackUsed,
            reason,
            actualTrigger.retryable());
    }

    private String sourceMode(String strategy) {
        return switch (strategy) {
            case "LOCAL_MODEL" -> "B1";
            case "EXTERNAL_MODEL" -> "B2";
            default -> "B0";
        };
    }

    private String normalize(String routeStrategy) {
        if (routeStrategy == null || routeStrategy.isBlank()) {
            return "BASELINE";
        }
        return routeStrategy.trim().toUpperCase(Locale.ROOT);
    }
}
