package com.medkernel.engine.rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 发布前规则静态冲突检测器。
 *
 * <p>触发点重叠由资产版本绑定层预先筛选；本检测器仅在同数值事实、条件区间重叠且
 * 一方阻断另一方非阻断时报告冲突，避免把普通的规则覆盖关系误判为医疗处置冲突。
 */
public final class RuleConflictDetector {

    /**
     * 查找候选规则与已发布规则之间的首个确定冲突。
     */
    public Optional<RuleConflict> detect(JsonNode candidate, List<RuleConflictTarget> targets) {
        if (candidate == null || targets == null || targets.isEmpty()) {
            return Optional.empty();
        }
        List<NumericCondition> candidateConditions = numericConditions(candidate.path("when"));
        boolean candidateBlocks = hasBlockingAction(candidate.path("then"));

        for (RuleConflictTarget target : targets) {
            if (target == null || target.dsl() == null) {
                continue;
            }
            if (!applicabilityMayOverlap(
                    candidate.path("applicability"),
                    target.dsl().path("applicability"))) {
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

    private static boolean applicabilityMayOverlap(JsonNode left, JsonNode right) {
        if (!left.isObject() || !right.isObject()) {
            return true;
        }
        if (!populationsMayOverlap(left.path("population"), right.path("population"))) {
            return false;
        }
        if (!arraysMayOverlap(left.path("settings"), right.path("settings"))) {
            return false;
        }
        JsonNode leftOrg = left.path("orgScope");
        JsonNode rightOrg = right.path("orgScope");
        if (!arraysMayOverlap(leftOrg.path("groupIds"), rightOrg.path("groupIds"))
                || !arraysMayOverlap(leftOrg.path("hospitalIds"), rightOrg.path("hospitalIds"))
                || !arraysMayOverlap(leftOrg.path("deptIds"), rightOrg.path("deptIds"))) {
            return false;
        }
        JsonNode leftEffective = left.path("effective");
        JsonNode rightEffective = right.path("effective");
        if (leftEffective.path("rolloutPercent").asInt(100) == 0
                || rightEffective.path("rolloutPercent").asInt(100) == 0) {
            return false;
        }
        LocalDate leftFrom = optionalDate(leftEffective.path("from"));
        LocalDate leftTo = optionalDate(leftEffective.path("to"));
        LocalDate rightFrom = optionalDate(rightEffective.path("from"));
        LocalDate rightTo = optionalDate(rightEffective.path("to"));
        return !endsBefore(leftTo, rightFrom) && !endsBefore(rightTo, leftFrom);
    }

    private static boolean populationsMayOverlap(JsonNode left, JsonNode right) {
        JsonNode leftInclude = left.path("include");
        JsonNode rightInclude = right.path("include");
        if (!leftInclude.isObject() || !rightInclude.isObject()) {
            return true;
        }
        Optional<List<NumericCondition>> leftConditions =
            conjunctiveNumericConditions(leftInclude);
        Optional<List<NumericCondition>> rightConditions =
            conjunctiveNumericConditions(rightInclude);
        if (leftConditions.isEmpty() || rightConditions.isEmpty()) {
            return true;
        }
        for (NumericCondition leftCondition : leftConditions.orElseThrow()) {
            for (NumericCondition rightCondition : rightConditions.orElseThrow()) {
                if (leftCondition.fact().equals(rightCondition.fact())
                        && !leftCondition.range().overlaps(rightCondition.range())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Optional<List<NumericCondition>> conjunctiveNumericConditions(JsonNode node) {
        if (node == null || !node.isObject() || node.has("any") || node.has("not")) {
            return Optional.empty();
        }
        JsonNode all = node.get("all");
        if (all != null) {
            if (!all.isArray()) {
                return Optional.empty();
            }
            List<NumericCondition> conditions = new ArrayList<>();
            for (JsonNode child : all) {
                Optional<List<NumericCondition>> childConditions =
                    conjunctiveNumericConditions(child);
                if (childConditions.isEmpty()) {
                    return Optional.empty();
                }
                conditions.addAll(childConditions.orElseThrow());
            }
            return Optional.of(conditions);
        }
        String fact = node.path("fact").asText(null);
        NumericRange range = NumericRange.from(
            node.path("operator").asText(null),
            node.get("value"));
        if (fact == null || fact.isBlank() || range == null) {
            return Optional.empty();
        }
        return Optional.of(List.of(new NumericCondition(fact, range)));
    }

    private static boolean arraysMayOverlap(JsonNode left, JsonNode right) {
        if (!left.isArray() || left.isEmpty() || !right.isArray() || right.isEmpty()) {
            return true;
        }
        Set<String> values = new HashSet<>();
        left.forEach(value -> {
            if (value.isTextual()) {
                values.add(value.asText());
            }
        });
        for (JsonNode value : right) {
            if (value.isTextual() && values.contains(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate optionalDate(JsonNode value) {
        return value != null && value.isTextual() && !value.asText().isBlank()
            ? LocalDate.parse(value.asText())
            : null;
    }

    private static boolean endsBefore(LocalDate end, LocalDate start) {
        return end != null && start != null && end.isBefore(start);
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
