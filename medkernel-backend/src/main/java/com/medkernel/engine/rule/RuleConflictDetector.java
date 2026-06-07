package com.medkernel.engine.rule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 发布前规则静态冲突检测器。
 *
 * <p>仅在同触发点、同数值事实、条件区间重叠且一方阻断另一方非阻断时报告冲突，
 * 避免把普通的规则覆盖关系误判为医疗处置冲突。
 */
public final class RuleConflictDetector {

    /**
     * 查找候选规则与已发布规则之间的首个确定冲突。
     */
    public Optional<RuleConflict> detect(JsonNode candidate, List<RuleConflictTarget> targets) {
        if (candidate == null || targets == null || targets.isEmpty()) {
            return Optional.empty();
        }
        String trigger = candidate.path("trigger").asText(null);
        List<NumericCondition> candidateConditions = numericConditions(candidate.path("when"));
        boolean candidateBlocks = hasBlockingAction(candidate.path("then"));

        for (RuleConflictTarget target : targets) {
            if (target == null || target.dsl() == null
                    || !sameText(trigger, target.dsl().path("trigger").asText(null))) {
                continue;
            }
            boolean targetBlocks = hasBlockingAction(target.dsl().path("then"));
            if (candidateBlocks == targetBlocks) {
                continue;
            }
            for (NumericCondition left : candidateConditions) {
                for (NumericCondition right : numericConditions(target.dsl().path("when"))) {
                    if (left.fact().equals(right.fact()) && left.range().overlaps(right.range())) {
                        return Optional.of(new RuleConflict(
                            target.ruleCode(),
                            left.fact(),
                            "同触发点同事实区间重叠且动作处置冲突"
                        ));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static List<NumericCondition> numericConditions(JsonNode node) {
        List<NumericCondition> conditions = new ArrayList<>();
        collectNumericConditions(node, conditions);
        return conditions;
    }

    private static void collectNumericConditions(JsonNode node, List<NumericCondition> conditions) {
        if (node == null || !node.isObject()) {
            return;
        }
        JsonNode all = node.get("all");
        JsonNode any = node.get("any");
        if (all != null && all.isArray()) {
            all.forEach(child -> collectNumericConditions(child, conditions));
            return;
        }
        if (any != null && any.isArray()) {
            any.forEach(child -> collectNumericConditions(child, conditions));
            return;
        }
        String fact = node.path("fact").asText(null);
        String operator = node.path("operator").asText(null);
        JsonNode value = node.get("value");
        NumericRange range = NumericRange.from(operator, value);
        if (fact != null && !fact.isBlank() && range != null) {
            conditions.add(new NumericCondition(fact, range));
        }
    }

    private static boolean hasBlockingAction(JsonNode actions) {
        if (actions == null || !actions.isArray()) {
            return false;
        }
        for (JsonNode action : actions) {
            if ("BLOCK".equals(action.path("actionCode").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameText(String left, String right) {
        return left != null && left.equals(right);
    }

    private record NumericCondition(String fact, NumericRange range) {}

    private record NumericRange(
        BigDecimal lower,
        boolean lowerInclusive,
        BigDecimal upper,
        boolean upperInclusive
    ) {
        static NumericRange from(String operator, JsonNode value) {
            if (operator == null || value == null || !value.isNumber()) {
                return null;
            }
            BigDecimal number = value.decimalValue();
            return switch (operator.toLowerCase(java.util.Locale.ROOT)) {
                case "gt" -> new NumericRange(number, false, null, false);
                case "gte" -> new NumericRange(number, true, null, false);
                case "lt" -> new NumericRange(null, false, number, false);
                case "lte" -> new NumericRange(null, false, number, true);
                case "equals" -> new NumericRange(number, true, number, true);
                default -> null;
            };
        }

        boolean overlaps(NumericRange other) {
            if (upper != null && other.lower != null) {
                int comparison = upper.compareTo(other.lower);
                if (comparison < 0 || (comparison == 0 && !(upperInclusive && other.lowerInclusive))) {
                    return false;
                }
            }
            if (other.upper != null && lower != null) {
                int comparison = other.upper.compareTo(lower);
                if (comparison < 0 || (comparison == 0 && !(other.upperInclusive && lowerInclusive))) {
                    return false;
                }
            }
            return true;
        }
    }
}
