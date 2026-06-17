package com.medkernel.engine.llm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LLM-02 B0/B1/B2 降级矩阵。
 *
 * <p>当前矩阵的安全策略是：B1/B2 任一运行期不可用或结构化失败均退回 B0；
 * B0 策略本身只记录显式基线归因，不伪造上级模型。
 */
public class ModelFallbackMatrix {

    private static final Set<String> ROUTE_STRATEGIES =
        Set.of("BASELINE", "LOCAL_MODEL", "EXTERNAL_MODEL");
    private static final Map<String, Integer> STRATEGY_RANK = Map.of(
        "EXTERNAL_MODEL", 3,
        "LOCAL_MODEL", 2,
        "BASELINE", 1
    );

    public ModelFallbackDecision decide(String routeStrategy, ModelFallbackTrigger trigger, String detail) {
        return decide(routeStrategy, "BASELINE", trigger, detail);
    }

    public ModelFallbackDecision decide(
            String routeStrategy,
            String fallbackStrategy,
            ModelFallbackTrigger trigger,
            String detail) {
        String strategy = normalizeRoute(routeStrategy);
        String fallback = normalizeRoute(fallbackStrategy);
        ModelFallbackTrigger actualTrigger = trigger == null ? ModelFallbackTrigger.PROVIDER_ERROR : trigger;
        String sourceMode = sourceMode(strategy);
        String fallbackMode = sourceMode(fallback);
        boolean fallbackUsed = !sourceMode.equals(fallbackMode);
        String action = "B0".equals(fallbackMode) ? "使用 B0 确定性基线" : "按 fallback_order 尝试下一级";
        String reason = "[LLM-02:" + actualTrigger.name() + "] " + sourceMode + " -> "
            + fallbackMode + "：" + action + "；" + actualTrigger.message();
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

    public List<String> defaultFallbackOrder(String routeStrategy) {
        return switch (normalizeRoute(routeStrategy)) {
            case "EXTERNAL_MODEL" -> List.of("EXTERNAL_MODEL", "LOCAL_MODEL", "BASELINE");
            case "LOCAL_MODEL" -> List.of("LOCAL_MODEL", "BASELINE");
            case "BASELINE" -> List.of("BASELINE");
            default -> List.of();
        };
    }

    public List<String> normalizeFallbackOrder(String routeStrategy, List<String> requestedOrder) {
        String strategy = normalizeRoute(routeStrategy);
        if ("DISABLED".equals(strategy)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        if (requestedOrder == null || requestedOrder.isEmpty()) {
            normalized.addAll(defaultFallbackOrder(strategy));
        } else {
            for (String item : requestedOrder) {
                normalized.add(normalizeRoute(item));
            }
        }
        String error = validateFallbackOrder(strategy, normalized);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        return List.copyOf(normalized);
    }

    public String validateFallbackOrder(String routeStrategy, List<String> fallbackOrder) {
        String strategy = normalizeRoute(routeStrategy);
        if ("DISABLED".equals(strategy)) {
            return fallbackOrder == null || fallbackOrder.isEmpty()
                ? null
                : "fallback_order：DISABLED 策略不得配置降级顺序";
        }
        List<String> normalized;
        try {
            normalized = fallbackOrder == null || fallbackOrder.isEmpty()
                ? defaultFallbackOrder(strategy)
                : fallbackOrder.stream().map(this::normalizeRoute).toList();
        } catch (RuntimeException invalid) {
            return "fallback_order：存在非法模型层级";
        }
        if (normalized.isEmpty()) {
            return "fallback_order：非禁用策略必须配置降级顺序";
        }
        if (!strategy.equals(normalized.getFirst())) {
            return "fallback_order：首项必须等于 route_strategy";
        }
        if (!"BASELINE".equals(normalized.getLast())) {
            return "fallback_order：最后一项必须是 BASELINE";
        }
        Set<String> seen = new LinkedHashSet<>();
        int previousRank = Integer.MAX_VALUE;
        for (String item : normalized) {
            if (!ROUTE_STRATEGIES.contains(item)) {
                return "fallback_order：仅允许 EXTERNAL_MODEL、LOCAL_MODEL、BASELINE";
            }
            if (!seen.add(item)) {
                return "fallback_order：不得重复配置模型层级";
            }
            int rank = STRATEGY_RANK.get(item);
            if (rank > previousRank) {
                return "fallback_order：只能从高层级向低层级降级";
            }
            previousRank = rank;
        }
        return null;
    }

    private String sourceMode(String strategy) {
        return switch (strategy) {
            case "LOCAL_MODEL" -> "B1";
            case "EXTERNAL_MODEL" -> "B2";
            default -> "B0";
        };
    }

    private String normalizeRoute(String routeStrategy) {
        if (routeStrategy == null || routeStrategy.isBlank()) {
            return "BASELINE";
        }
        return routeStrategy.trim().toUpperCase(Locale.ROOT);
    }
}
