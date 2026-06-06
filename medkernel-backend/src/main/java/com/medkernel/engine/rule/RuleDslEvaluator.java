package com.medkernel.engine.rule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 规则 JSON DSL 确定性执行器（GA-ENG-API-05 § 7）。
 *
 * <p>负责把 {@code when} 条件树（all/any/leaf）按上下文求值，命中后解析 {@code then} 动作并计算最高严重度。
 * 覆盖基础比较、集合判断以及 between/unit_compare/temporal/derived 临床算子。
 * 缺失普通字段不抛内部异常，而是产生未命中；受控公式缺少必需参数时返回
 * {@code INSUFFICIENT_DATA}，避免临床计算臆测默认值。
 */
@Component
public class RuleDslEvaluator {

    private final ObjectMapper json;
    private final ClinicalRuleOperatorSupport clinicalOperators;

    /**
     * 注入 JSON 处理器，用于缺省上下文、缺失节点与 DSL 条件树解析。
     */
    public RuleDslEvaluator(ObjectMapper json) {
        this.json = json;
        this.clinicalOperators = new ClinicalRuleOperatorSupport(json);
    }

    /**
     * 对指定 DSL 和上下文执行一次确定性求值。
     *
     * <p>DSL 必须为 JSON 对象且至少包含 {@code when} 条件；条件未命中返回空动作集，
     * 命中则解析 {@code then} 动作并计算最高严重度。校验失败抛
     * {@link com.medkernel.shared.api.error.ApiException} 错误码 {@code ENG-RULE-001}。
     */
    public RuleDslEvaluation evaluate(JsonNode dsl, JsonNode context) {
        if (dsl == null || !dsl.isObject()) {
            throw invalid("规则 DSL 必须是 JSON 对象");
        }
        JsonNode when = dsl.get("when");
        if (when == null || !when.isObject()) {
            throw invalid("规则 DSL 缺少 when 条件");
        }
        JsonNode evalContext = withDerivedFields(context == null ? json.createObjectNode() : context);
        ConditionEvaluation condition = evaluateConditionNode(when, evalContext);
        JsonNode explanation = buildExplanation(dsl.path("explain"), condition.evidence());
        if (!condition.matched()) {
            return new RuleDslEvaluation(false, null, List.of(), explanation);
        }

        List<RuleActionResult> actions = parseActions(dsl.path("then"));
        RuleRiskLevel highest = actions.stream()
            .map(RuleActionResult::severity)
            .reduce(null, RuleRiskLevel::max);
        return new RuleDslEvaluation(true, highest, actions, explanation);
    }

    /**
     * 求值期补齐派生字段：{@code patient.age} 由 {@code patient.birthDate} 与 {@code patient.eventTime}
     * 计算整岁（评估时刻取数据内 eventTime、可复现，不用 wall-clock）。缺 birthDate/eventTime 不补、
     * 已显式带 age 不覆盖（按缺失数据策略，不臆测）。返回增强副本，不改调用方上下文。
     */
    private JsonNode withDerivedFields(JsonNode context) {
        if (!context.isObject()) {
            return context;
        }
        JsonNode patient = context.path("patient");
        if (!patient.isObject() || patient.has("age")) {
            return context;
        }
        Integer age = derivePatientAge(patient);
        if (age == null) {
            return context;
        }
        ObjectNode augmented = (ObjectNode) context.deepCopy();
        ((ObjectNode) augmented.get("patient")).put("age", age);
        return augmented;
    }

    private Integer derivePatientAge(JsonNode patient) {
        LocalDate birthDate = parseLocalDate(patient.path("birthDate"));
        Instant asOf = parseInstant(patient.path("eventTime"));
        if (birthDate == null || asOf == null) {
            return null;
        }
        LocalDate asOfDate = asOf.atZone(ZoneOffset.UTC).toLocalDate();
        if (asOfDate.isBefore(birthDate)) {
            return null; // 出生日期晚于评估时刻：数据异常，不臆测负年龄
        }
        return Period.between(birthDate, asOfDate).getYears();
    }

    private LocalDate parseLocalDate(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Instant parseInstant(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private ConditionEvaluation evaluateConditionNode(JsonNode node, JsonNode context) {
        JsonNode all = node.get("all");
        if (all != null) {
            if (!all.isArray()) {
                throw invalid("when.all 必须是数组");
            }
            List<ConditionEvidence> evidence = new ArrayList<>();
            for (JsonNode child : all) {
                ConditionEvaluation result = evaluateConditionNode(child, context);
                evidence.addAll(result.evidence());
                if (!result.matched()) {
                    return new ConditionEvaluation(false, List.copyOf(evidence));
                }
            }
            return new ConditionEvaluation(true, List.copyOf(evidence));
        }

        JsonNode any = node.get("any");
        if (any != null) {
            if (!any.isArray()) {
                throw invalid("when.any 必须是数组");
            }
            List<ConditionEvidence> evidence = new ArrayList<>();
            for (JsonNode child : any) {
                ConditionEvaluation result = evaluateConditionNode(child, context);
                evidence.addAll(result.evidence());
                if (result.matched()) {
                    return new ConditionEvaluation(true, List.copyOf(evidence));
                }
            }
            return new ConditionEvaluation(false, List.copyOf(evidence));
        }

        return evaluateLeaf(node, context);
    }

    private ConditionEvaluation evaluateLeaf(JsonNode node, JsonNode context) {
        String fact = requiredText(node, "fact");
        String operator = requiredText(node, "operator").toLowerCase(Locale.ROOT);
        JsonNode actual = findPath(context, fact);
        JsonNode expected = node.get("value");

        ClinicalRuleOperatorSupport.Outcome outcome = switch (operator) {
            case "exists" -> clinicalOperators.basicOutcome(exists(actual), actual, expected);
            case "equals" -> clinicalOperators.basicOutcome(exists(actual) && valuesEqual(actual, expected), actual, expected);
            case "not_equals" -> clinicalOperators.basicOutcome(!exists(actual) || !valuesEqual(actual, expected), actual, expected);
            case "contains" -> clinicalOperators.basicOutcome(contains(actual, expected), actual, expected);
            case "gt" -> clinicalOperators.basicOutcome(compare(actual, expected, "gt"), actual, expected);
            case "gte" -> clinicalOperators.basicOutcome(compare(actual, expected, "gte"), actual, expected);
            case "lt" -> clinicalOperators.basicOutcome(compare(actual, expected, "lt"), actual, expected);
            case "lte" -> clinicalOperators.basicOutcome(compare(actual, expected, "lte"), actual, expected);
            case "in" -> clinicalOperators.basicOutcome(in(actual, expected), actual, expected);
            case "not_in" -> clinicalOperators.basicOutcome(!in(actual, expected), actual, expected);
            case "between" -> clinicalOperators.between(fact, actual, expected);
            case "not_between" -> clinicalOperators.notBetween(fact, actual, expected);
            case "within_ref" -> clinicalOperators.referenceRange(fact, actual, "within");
            case "above_ref" -> clinicalOperators.referenceRange(fact, actual, "above");
            case "below_ref" -> clinicalOperators.referenceRange(fact, actual, "below");
            case "is_missing" -> clinicalOperators.isMissing(actual);
            case "is_critical" -> clinicalOperators.isCritical(actual, expected);
            case "is_stale" -> clinicalOperators.isStale(fact, actual, expected);
            case "unit_compare" -> clinicalOperators.unitCompare(fact, actual, expected);
            case "temporal" -> clinicalOperators.temporal(fact, actual, expected);
            case "derived" -> clinicalOperators.derived(fact, context, expected);
            default -> throw operatorInvalid("不支持的规则算子: " + operator);
        };
        return new ConditionEvaluation(
            outcome.matched(),
            List.of(new ConditionEvidence(
                fact,
                "$." + fact,
                operator,
                outcome.expected(),
                outcome.actual(),
                outcome.matched(),
                outcome.missing(),
                outcome.value(),
                outcome.unit(),
                outcome.source(),
                outcome.formula())));
    }

    private List<RuleActionResult> parseActions(JsonNode then) {
        if (then == null || then.isMissingNode()) {
            return List.of();
        }
        if (!then.isArray()) {
            throw invalid("then 必须是数组");
        }
        List<RuleActionResult> actions = new ArrayList<>();
        for (JsonNode action : then) {
            String actionCode = requiredText(action, "actionCode");
            RuleRiskLevel severity = parseSeverity(action.path("severity").asText(null));
            boolean requires = action.path("requiresPhysicianConfirmation").asBoolean(false)
                || requiresConfirmation(actionCode, severity);
            actions.add(new RuleActionResult(
                actionCode, severity, action.path("message").asText(null), requires));
        }
        return actions;
    }

    private JsonNode findPath(JsonNode source, String path) {
        JsonNode current = source;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return json.missingNode();
            }
            current = current.path(segment);
        }
        return current == null ? json.missingNode() : current;
    }

    private JsonNode buildExplanation(JsonNode source, List<ConditionEvidence> evidence) {
        ObjectNode explanation;
        if (source != null && source.isObject()) {
            explanation = source.deepCopy();
        } else {
            explanation = json.createObjectNode();
            if (source != null && !source.isMissingNode() && !source.isNull()) {
                explanation.set("summary", safeNode(source));
            }
        }

        ArrayNode conditionEvidence = json.createArrayNode();
        for (ConditionEvidence item : evidence) {
            ObjectNode entry = json.createObjectNode();
            entry.put("fact", item.fact());
            entry.put("sourcePath", item.sourcePath());
            entry.put("operator", item.operator());
            entry.set("expected", safeNode(item.expected()));
            entry.set("actual", safeNode(item.actual()));
            entry.put("matched", item.matched());
            entry.put("missing", item.missing());
            if (item.value() != null && !item.value().isMissingNode() && !item.value().isNull()) {
                entry.set("value", safeNode(item.value()));
            }
            if (item.unit() != null && !item.unit().isBlank()) {
                entry.put("unit", item.unit());
            }
            if (item.source() != null && !item.source().isBlank()) {
                entry.put("source", item.source());
            }
            if (item.formula() != null && !item.formula().isBlank()) {
                entry.put("formula", item.formula());
            }
            conditionEvidence.add(entry);
        }
        explanation.set("conditionEvidence", conditionEvidence);
        return explanation;
    }

    private JsonNode safeNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return json.nullNode();
        }
        return node.deepCopy();
    }

    private boolean exists(JsonNode actual) {
        if (actual == null || actual.isMissingNode() || actual.isNull()) {
            return false;
        }
        if (actual.isTextual()) {
            return !actual.asText().isBlank();
        }
        if (actual.isArray() || actual.isObject()) {
            return actual.size() > 0;
        }
        return true;
    }

    private boolean valuesEqual(JsonNode actual, JsonNode expected) {
        if (expected == null || expected.isMissingNode()) {
            return !exists(actual);
        }
        if (actual.isNumber() && expected.isNumber()) {
            return actual.decimalValue().compareTo(expected.decimalValue()) == 0;
        }
        if (actual.isBoolean() || expected.isBoolean()) {
            return actual.asBoolean() == expected.asBoolean();
        }
        if (actual.isTextual() || expected.isTextual()) {
            return actual.asText().equals(expected.asText());
        }
        return actual.equals(expected);
    }

    private boolean contains(JsonNode actual, JsonNode expected) {
        if (!exists(actual) || expected == null || expected.isMissingNode()) {
            return false;
        }
        if (actual.isArray()) {
            Iterator<JsonNode> values = actual.elements();
            while (values.hasNext()) {
                if (valuesEqual(values.next(), expected)) {
                    return true;
                }
            }
            return false;
        }
        return actual.isTextual() && actual.asText().contains(expected.asText());
    }

    private boolean in(JsonNode actual, JsonNode expected) {
        if (!exists(actual) || expected == null || !expected.isArray()) {
            return false;
        }
        for (JsonNode item : expected) {
            if (valuesEqual(actual, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean compare(JsonNode actual, JsonNode expected, String operator) {
        if (!exists(actual) || expected == null || !actual.isNumber() || !expected.isNumber()) {
            return false;
        }
        BigDecimal left = actual.decimalValue();
        BigDecimal right = expected.decimalValue();
        int compared = left.compareTo(right);
        return switch (operator) {
            case "gt" -> compared > 0;
            case "gte" -> compared >= 0;
            case "lt" -> compared < 0;
            case "lte" -> compared <= 0;
            default -> throw invalid("不支持的数值比较算子: " + operator);
        };
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw invalid("规则 DSL 缺少字段: " + field);
        }
        return value;
    }

    private RuleRiskLevel parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return RuleRiskLevel.LOW;
        }
        try {
            return RuleRiskLevel.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("规则风险级别无效: " + value);
        }
    }

    private boolean requiresConfirmation(String actionCode, RuleRiskLevel severity) {
        return severity == RuleRiskLevel.HIGH
            || severity == RuleRiskLevel.CRITICAL
            || "BLOCK".equals(actionCode)
            || "STRONG_REMINDER".equals(actionCode)
            || "RECOMMEND_NEXT".equals(actionCode);
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.RULE_DSL_INVALID, message);
    }

    private ApiException operatorInvalid(String message) {
        return new ApiException(ErrorCode.DSL_OPERATOR_INVALID, message);
    }

    private record ConditionEvaluation(boolean matched, List<ConditionEvidence> evidence) {
    }

    private record ConditionEvidence(
        String fact,
        String sourcePath,
        String operator,
        JsonNode expected,
        JsonNode actual,
        boolean matched,
        boolean missing,
        JsonNode value,
        String unit,
        String source,
        String formula) {
    }
}
