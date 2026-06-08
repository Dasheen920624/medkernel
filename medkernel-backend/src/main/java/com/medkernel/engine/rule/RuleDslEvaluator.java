package com.medkernel.engine.rule;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdshook.CdsHookSource;
import com.medkernel.engine.cdshook.CdsHookSuggestion;
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
    private final ConditionEvaluator conditionEvaluator;

    /**
     * 注入 JSON 处理器，用于缺省上下文、缺失节点与 DSL 条件树解析。
     */
    @Autowired
    public RuleDslEvaluator(ObjectMapper json, ConditionEvaluator conditionEvaluator) {
        this.json = json;
        this.conditionEvaluator = conditionEvaluator == null ? new ConditionEvaluator(json) : conditionEvaluator;
    }

    public RuleDslEvaluator(ObjectMapper json) {
        this(json, new ConditionEvaluator(json));
    }

    /**
     * 对指定 DSL 和上下文执行一次确定性求值。
     *
     * <p>DSL 必须为 JSON 对象且至少包含 {@code when} 条件；先校验并解析 {@code then} 动作，
     * 再执行条件树。条件未命中返回空动作集，命中则计算最高严重度。校验失败抛
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
        List<RuleActionResult> actions = parseActions(dsl.path("then"));
        MissingPolicy missingPolicy = parseMissingPolicy(dsl.path("missingPolicy").asText(null));
        ConditionEvaluation condition = conditionEvaluator.evaluate(
            when,
            context == null ? json.createObjectNode() : context);
        boolean unknownBlocked = !condition.matched()
            && condition.unknown()
            && missingPolicy == MissingPolicy.UNKNOWN_AS_BLOCK;
        JsonNode explanation = buildExplanation(
            dsl.path("explain"), condition.evidence(), missingPolicy, unknownBlocked);
        if (!condition.matched() && !unknownBlocked) {
            return new RuleDslEvaluation(false, null, List.of(), explanation);
        }
        if (unknownBlocked) {
            actions = actions.stream()
                .map(this::forceManualReview)
                .toList();
        }

        RuleRiskLevel highest = actions.stream()
            .map(RuleActionResult::severity)
            .reduce(null, RuleRiskLevel::max);
        return new RuleDslEvaluation(true, highest, actions, explanation);
    }

    private List<RuleActionResult> parseActions(JsonNode then) {
        if (!then.isArray()) {
            throw invalid("then 必须是数组");
        }
        if (then.isEmpty()) {
            throw invalid("then 至少包含一个动作卡");
        }
        List<RuleActionResult> actions = new ArrayList<>();
        for (JsonNode action : then) {
            if (!action.isObject()) {
                throw invalid("then 动作必须是 JSON 对象");
            }
            RuleRiskLevel severity = parseSeverity(requiredText(action, "atSeverity"));
            RuleActionCode actionCode = parseActionCode(requiredText(action, "actionCode"));
            String indicator = parseIndicator(requiredText(action, "indicator"));
            String summary = requiredText(action, "summary");
            String detail = requiredText(action, "detail");
            CdsHookSource source = parseSource(action.path("source"));
            List<CdsHookSuggestion> suggestions = parseSuggestions(action.path("suggestions"));
            List<String> overrideReasons = parseOverrideReasons(action.path("overrideReasons"));
            boolean requires = action.path("requiresPhysicianConfirmation").asBoolean(false)
                || requiresConfirmation(actionCode, severity);
            actions.add(new RuleActionResult(
                actionCode,
                severity,
                indicator,
                summary,
                detail,
                source,
                suggestions,
                overrideReasons,
                requires));
        }
        return actions;
    }

    private CdsHookSource parseSource(JsonNode source) {
        if (!source.isObject()) {
            throw invalid("规则 DSL 缺少字段: source");
        }
        return new CdsHookSource(
            requiredText(source, "label"),
            optionalText(source, "url"),
            optionalText(source, "evidenceLevel"));
    }

    private List<CdsHookSuggestion> parseSuggestions(JsonNode source) {
        if (!source.isArray()) {
            throw invalid("规则 DSL 字段 suggestions 必须是数组");
        }
        List<CdsHookSuggestion> suggestions = new ArrayList<>();
        for (JsonNode suggestion : source) {
            if (!suggestion.isObject()) {
                throw invalid("规则 DSL 建议项必须是 JSON 对象");
            }
            suggestions.add(new CdsHookSuggestion(
                requiredText(suggestion, "label"),
                requiredText(suggestion, "actionType"),
                suggestion.path("payload")));
        }
        return suggestions;
    }

    private List<String> parseOverrideReasons(JsonNode source) {
        if (!source.isArray()) {
            throw invalid("规则 DSL 字段 overrideReasons 必须是数组");
        }
        List<String> reasons = new ArrayList<>();
        for (JsonNode reason : source) {
            if (!reason.isTextual() || reason.asText().isBlank()) {
                throw invalid("规则 DSL overrideReasons 仅允许非空文本");
            }
            reasons.add(reason.asText());
        }
        return reasons;
    }

    private JsonNode buildExplanation(
        JsonNode source,
        List<ConditionEvidence> evidence,
        MissingPolicy missingPolicy,
        boolean unknownBlocked
    ) {
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
        explanation.put("missingPolicy", missingPolicy.name());
        if (unknownBlocked) {
            explanation.put("unknownBlocked", true);
            explanation.put("manualReviewRequired", true);
            explanation.put("manualReviewReason", "missingPolicy=UNKNOWN_AS_BLOCK，关键事实未知时必须人工核查");
        }
        return explanation;
    }

    private RuleActionResult forceManualReview(RuleActionResult action) {
        return new RuleActionResult(
            action.actionCode(),
            action.severity(),
            action.indicator(),
            action.summary(),
            action.detail(),
            action.source(),
            action.suggestions(),
            action.overrideReasons(),
            true);
    }

    private JsonNode safeNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return json.nullNode();
        }
        return node.deepCopy();
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw invalid("规则 DSL 缺少字段: " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private RuleRiskLevel parseSeverity(String value) {
        try {
            return RuleRiskLevel.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("规则风险级别无效: " + value);
        }
    }

    private RuleActionCode parseActionCode(String value) {
        try {
            return RuleActionCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("规则动作码无效: " + value);
        }
    }

    private String parseIndicator(String value) {
        if ("info".equals(value) || "warning".equals(value) || "critical".equals(value)) {
            return value;
        }
        throw invalid("规则卡片 indicator 无效: " + value);
    }

    private MissingPolicy parseMissingPolicy(String value) {
        if (value == null || value.isBlank()) {
            return MissingPolicy.UNKNOWN_AS_FALSE;
        }
        try {
            return MissingPolicy.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("missingPolicy 无效: " + value);
        }
    }

    private boolean requiresConfirmation(RuleActionCode actionCode, RuleRiskLevel severity) {
        return severity == RuleRiskLevel.HIGH
            || severity == RuleRiskLevel.CRITICAL
            || actionCode == RuleActionCode.BLOCK
            || actionCode == RuleActionCode.STRONG_REMINDER
            || actionCode == RuleActionCode.SUGGEST_ORDER;
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_RULE_001, message);
    }

    private enum MissingPolicy {
        UNKNOWN_AS_FALSE,
        UNKNOWN_AS_BLOCK
    }

}
