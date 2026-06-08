package com.medkernel.engine.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.authoring.AuthoringFeatureFlag;
import com.medkernel.engine.authoring.AuthoringFeatureGate;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.SystemConfigService;

/**
 * 规则与路径共用的确定性条件求值内核。
 *
 * <p>支持统一 {@code all}/{@code any}/{@code not}+叶子文法，对 canonical 上下文求值。
 */
@Component
public class ConditionEvaluator {

    private final ObjectMapper json;
    private final ClinicalRuleOperatorSupport clinicalOperators;
    private final AuthoringFeatureGate featureGate;

    public ConditionEvaluator(ObjectMapper json) {
        this(json, AuthoringFeatureGate.alwaysEnabled());
    }

    @Autowired
    public ConditionEvaluator(ObjectMapper json, AuthoringFeatureGate featureGate) {
        this.json = json;
        this.featureGate = featureGate == null ? AuthoringFeatureGate.alwaysEnabled() : featureGate;
        this.clinicalOperators = new ClinicalRuleOperatorSupport(json);
    }

    public ConditionEvaluation evaluate(JsonNode condition, JsonNode context) {
        if (condition == null || !condition.isObject()) {
            throw invalid("条件必须是 JSON 对象");
        }
        JsonNode evalContext = withDerivedFields(context == null ? json.createObjectNode() : context);
        return evaluateConditionNode(condition, evalContext, 0);
    }

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
        var augmented = (com.fasterxml.jackson.databind.node.ObjectNode) context.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) augmented.get("patient")).put("age", age);
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
            return null;
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

    private ConditionEvaluation evaluateConditionNode(JsonNode node, JsonNode context, int depth) {
        if (depth > 0 && isConditionGroup(node)) {
            requireFeatureEnabled(AuthoringFeatureFlag.RECURSIVE_CONDITION_TREE);
        }
        JsonNode all = node.get("all");
        if (all != null) {
            if (!all.isArray()) {
                throw invalid("when.all 必须是数组");
            }
            List<ConditionEvidence> evidence = new ArrayList<>();
            for (JsonNode child : all) {
                ConditionEvaluation result = evaluateConditionNode(child, context, depth + 1);
                evidence.addAll(result.evidence());
                if (!result.matched()) {
                    return new ConditionEvaluation(false, evidence);
                }
            }
            return new ConditionEvaluation(true, evidence);
        }

        JsonNode any = node.get("any");
        if (any != null) {
            if (!any.isArray()) {
                throw invalid("when.any 必须是数组");
            }
            List<ConditionEvidence> evidence = new ArrayList<>();
            for (JsonNode child : any) {
                ConditionEvaluation result = evaluateConditionNode(child, context, depth + 1);
                evidence.addAll(result.evidence());
                if (result.matched()) {
                    return new ConditionEvaluation(true, evidence);
                }
            }
            return new ConditionEvaluation(false, evidence);
        }

        JsonNode not = node.get("not");
        if (not != null) {
            if (!not.isObject()) {
                throw invalid("when.not 必须是对象");
            }
            ConditionEvaluation result = evaluateConditionNode(not, context, depth + 1);
            return new ConditionEvaluation(!result.matched(), result.evidence());
        }

        return evaluateLeaf(node, context);
    }

    private ConditionEvaluation evaluateLeaf(JsonNode node, JsonNode context) {
        String operator = requiredText(node, "operator").toLowerCase(Locale.ROOT);
        if (isClinicalOperator(operator)) {
            requireFeatureEnabled(AuthoringFeatureFlag.CLINICAL_OPERATORS);
        }
        LeafActual leaf = resolveLeafActual(node, context);
        String fact = leaf.fact();
        JsonNode actual = leaf.actual();
        JsonNode expected = normalizeOperand(node.get("value"), context);

        if (hasInvalidQuality(actual)) {
            return new ConditionEvaluation(
                false,
                List.of(new ConditionEvidence(
                    fact,
                    leaf.sourcePath(),
                    operator,
                    expected,
                    actual,
                    false,
                    true,
                    null,
                    null,
                    leaf.source(),
                    leaf.formula())));
        }

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
                leaf.sourcePath(),
                operator,
                outcome.expected(),
                outcome.actual(),
                outcome.matched(),
                outcome.missing(),
                outcome.value(),
                outcome.unit(),
                outcome.source() == null ? leaf.source() : outcome.source(),
                combineFormula(outcome.formula(), leaf.formula()))));
    }

    private LeafActual resolveLeafActual(JsonNode node, JsonNode context) {
        JsonNode expr = node.get("expr");
        if (expr != null) {
            return evaluateExpression(expr, context);
        }
        String fact = requiredText(node, "fact");
        JsonNode actual = findPath(context, fact);
        return new LeafActual(fact, "$." + fact, actual, sourceFromActual(actual), qualityFormula(actual));
    }

    private LeafActual evaluateExpression(JsonNode expr, JsonNode context) {
        requireObject(expr, "expr");
        String field = requiredText(expr, "field");
        String select = optionalText(expr, "select");
        if (select == null) {
            JsonNode actual = findPath(context, field);
            if (actual.isArray() && actual.size() == 1) {
                actual = actual.get(0);
            }
            return new LeafActual(field, "$." + field, actual, sourceFromActual(actual), qualityFormula(actual));
        }

        ExpressionCollection collection = expressionCollection(field, context);
        List<ExpressionItem> candidates = new ArrayList<>(collection.items());
        JsonNode where = expr.get("where");
        if (where != null) {
            requireObject(where, "expr.where");
            candidates = candidates.stream()
                .filter(item -> evaluateConditionNode(
                    where,
                    singletonArrayContext(context, collection.arrayPath(), item.rawItem()),
                    0)
                    .matched())
                .toList();
        }
        String over = optionalText(expr, "over");
        if (over != null) {
            Duration window = parseExpressionWindow(over);
            Instant referenceTime = expressionReferenceTime(expr, context);
            Instant start = referenceTime.minus(window);
            candidates = candidates.stream()
                .filter(item -> {
                    Instant observedAt = item.observedAt();
                    if (observedAt == null) {
                        throw insufficientData("表达式 " + field + " 窗口过滤缺少时间戳");
                    }
                    return !observedAt.isBefore(start) && !observedAt.isAfter(referenceTime);
                })
                .toList();
        }

        ExpressionAggregate aggregate = aggregateExpression(field, select, candidates);
        String formula = "expr " + select.toLowerCase(Locale.ROOT) + "(" + field + ")"
            + (over == null ? "" : " over " + over)
            + " matched " + candidates.size() + "/" + collection.totalCount();
        return new LeafActual(field, "$." + field, aggregate.actual(), aggregate.source(), formula);
    }

    private JsonNode normalizeOperand(JsonNode value, JsonNode context) {
        if (value == null || value.isMissingNode()) {
            return value;
        }
        if (value.isObject()) {
            if (value.has("const")) {
                return value.get("const");
            }
            String field = optionalText(value, "field");
            if (field != null) {
                return findPath(context, field);
            }
        }
        return value;
    }

    private ExpressionAggregate aggregateExpression(String field, String select, List<ExpressionItem> candidates) {
        String normalized = select.toLowerCase(Locale.ROOT);
        if ("count".equals(normalized)) {
            return new ExpressionAggregate(numberNode(BigDecimal.valueOf(candidates.size())),
                joinExpressionSources(candidates));
        }
        if (candidates.isEmpty()) {
            return new ExpressionAggregate(json.nullNode(), null);
        }
        return switch (normalized) {
            case "latest" -> {
                ExpressionItem item = sortedByObservedAt(field, candidates).getLast();
                yield new ExpressionAggregate(safeNode(item.value()), item.source());
            }
            case "first" -> {
                ExpressionItem item = sortedByObservedAt(field, candidates).getFirst();
                yield new ExpressionAggregate(safeNode(item.value()), item.source());
            }
            case "max" -> numericExtreme(field, candidates, true);
            case "min" -> numericExtreme(field, candidates, false);
            case "avg" -> numericAverage(field, candidates);
            case "sum" -> numericSum(field, candidates);
            default -> throw operatorInvalid("不支持的 expr.select: " + select);
        };
    }

    private List<ExpressionItem> sortedByObservedAt(String field, List<ExpressionItem> items) {
        for (ExpressionItem item : items) {
            if (item.observedAt() == null) {
                throw insufficientData("表达式 " + field + " 缺少时间戳，无法稳定排序");
            }
        }
        return items.stream()
            .sorted(Comparator
                .comparing(ExpressionItem::observedAt)
                .thenComparing(item -> item.source() == null ? "" : item.source()))
            .toList();
    }

    private ExpressionAggregate numericExtreme(String field, List<ExpressionItem> items, boolean max) {
        ExpressionItem selected = null;
        for (ExpressionItem item : items) {
            BigDecimal value = numericExpressionValue(field, item);
            if (selected == null) {
                selected = item;
                continue;
            }
            BigDecimal selectedValue = numericExpressionValue(field, selected);
            int compared = value.compareTo(selectedValue);
            if ((max && compared > 0) || (!max && compared < 0)) {
                selected = item;
            }
        }
        return new ExpressionAggregate(numberNode(numericExpressionValue(field, selected)), selected.source());
    }

    private ExpressionAggregate numericAverage(String field, List<ExpressionItem> items) {
        BigDecimal sum = numericSumValue(field, items);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(items.size()), 8, RoundingMode.HALF_UP).stripTrailingZeros();
        return new ExpressionAggregate(numberNode(avg), joinExpressionSources(items));
    }

    private ExpressionAggregate numericSum(String field, List<ExpressionItem> items) {
        return new ExpressionAggregate(numberNode(numericSumValue(field, items).stripTrailingZeros()),
            joinExpressionSources(items));
    }

    private BigDecimal numericSumValue(String field, List<ExpressionItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpressionItem item : items) {
            sum = sum.add(numericExpressionValue(field, item));
        }
        return sum;
    }

    private BigDecimal numericExpressionValue(String field, ExpressionItem item) {
        if (item == null || !exists(item.value()) || !item.value().isNumber()) {
            throw insufficientData("表达式 " + field + " 聚合值必须是数值");
        }
        return item.value().decimalValue();
    }

    private ExpressionCollection expressionCollection(String field, JsonNode context) {
        int arrayMarker = field.indexOf("[].");
        if (arrayMarker < 0 && field.endsWith("[]")) {
            arrayMarker = field.length() - 2;
        }
        if (arrayMarker < 0) {
            JsonNode actual = findPath(context, field);
            if (actual != null && actual.isArray()) {
                List<ExpressionItem> items = new ArrayList<>();
                for (JsonNode item : actual) {
                    if (exists(item)) {
                        items.add(new ExpressionItem(item, item, firstText(item, "source", "sourceRef", "id"),
                            optionalInstant(item, "observedAt", "time", "effectiveTime", "recordedAt", "eventTime")));
                    }
                }
                return new ExpressionCollection(field, items, actual.size());
            }
            return new ExpressionCollection(field, exists(actual)
                ? List.of(new ExpressionItem(actual, actual, null, null))
                : List.of(), exists(actual) ? 1 : 0);
        }

        String arrayPath = field.substring(0, arrayMarker);
        String valuePath = field.length() > arrayMarker + 3 ? field.substring(arrayMarker + 3) : "";
        JsonNode array = findPath(context, arrayPath);
        if (!array.isArray()) {
            return new ExpressionCollection(arrayPath, List.of(), 0);
        }
        List<ExpressionItem> items = new ArrayList<>();
        for (JsonNode rawItem : array) {
            JsonNode value = valuePath.isBlank() ? rawItem : findPath(rawItem, valuePath);
            if (exists(value)) {
                items.add(new ExpressionItem(
                    value,
                    rawItem,
                    firstText(rawItem, "source", "sourceRef", "id"),
                    optionalInstant(rawItem, "observedAt", "time", "effectiveTime", "recordedAt", "eventTime")));
            }
        }
        return new ExpressionCollection(arrayPath, items, array.size());
    }

    private JsonNode singletonArrayContext(JsonNode context, String arrayPath, JsonNode rawItem) {
        if (!context.isObject()) {
            return context;
        }
        ObjectNode copy = context.deepCopy();
        ArrayNode singleton = json.createArrayNode();
        singleton.add(rawItem.deepCopy());
        setPath(copy, arrayPath.split("\\."), 0, singleton);
        return copy;
    }

    private void setPath(ObjectNode root, String[] segments, int index, JsonNode value) {
        if (index >= segments.length - 1) {
            root.set(segments[index], value);
            return;
        }
        JsonNode next = root.path(segments[index]);
        ObjectNode child = next.isObject() ? (ObjectNode) next.deepCopy() : json.createObjectNode();
        root.set(segments[index], child);
        setPath(child, segments, index + 1, value);
    }

    private Duration parseExpressionWindow(String value) {
        try {
            Duration window = Duration.parse(value);
            if (window.isZero() || window.isNegative()) {
                throw invalid("expr.over 必须大于 0: " + value);
            }
            return window;
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw invalid("expr.over 必须是 ISO-8601 Duration: " + value);
        }
    }

    private Instant expressionReferenceTime(JsonNode expr, JsonNode context) {
        String explicit = optionalText(expr, "referenceTime");
        if (explicit != null) {
            return parseRequiredInstant("expr.referenceTime", explicit);
        }
        String fromContext = firstText(context, "evaluationTime", "eventTime", "referenceTime");
        if (fromContext == null && context.path("patient").isObject()) {
            fromContext = firstText(context.path("patient"), "eventTime", "referenceTime");
        }
        if (fromContext == null && context.path("encounter").isObject()) {
            fromContext = firstText(context.path("encounter"), "eventTime", "referenceTime");
        }
        if (fromContext == null) {
            throw insufficientData("expr.over 缺少 referenceTime，无法确定时间窗");
        }
        return parseRequiredInstant("expr.referenceTime", fromContext);
    }

    private Instant parseRequiredInstant(String label, String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw invalid(label + " 必须是 ISO-8601 Instant: " + value);
        }
    }

    private JsonNode numberNode(BigDecimal value) {
        return json.getNodeFactory().numberNode(value);
    }

    private JsonNode safeNode(JsonNode node) {
        return node == null || node.isMissingNode() ? json.nullNode() : node.deepCopy();
    }

    private String joinExpressionSources(List<ExpressionItem> items) {
        List<String> sources = items.stream()
            .map(ExpressionItem::source)
            .filter(source -> source != null && !source.isBlank())
            .toList();
        return sources.isEmpty() ? null : String.join(",", sources);
    }

    private boolean hasInvalidQuality(JsonNode actual) {
        String status = qualityStatus(actual);
        return status != null && "INVALID".equalsIgnoreCase(status);
    }

    private String qualityFormula(JsonNode actual) {
        String status = qualityStatus(actual);
        if (status == null || "VALID".equalsIgnoreCase(status)) {
            return null;
        }
        return "qualityStatus=" + status.toUpperCase(Locale.ROOT);
    }

    private String qualityStatus(JsonNode actual) {
        if (actual == null || !actual.isObject()) {
            return null;
        }
        return optionalText(actual, "qualityStatus");
    }

    private String sourceFromActual(JsonNode actual) {
        if (actual == null || !actual.isObject()) {
            return null;
        }
        return firstText(actual, "source", "sourceRef", "id");
    }

    private String combineFormula(String primary, String secondary) {
        if (primary == null || primary.isBlank()) {
            return secondary;
        }
        if (secondary == null || secondary.isBlank()) {
            return primary;
        }
        return primary + "; " + secondary;
    }

    private JsonNode findPath(JsonNode source, String path) {
        return findPath(source, path.split("\\."), 0);
    }

    private JsonNode findPath(JsonNode current, String[] segments, int index) {
        if (current == null || current.isMissingNode() || current.isNull()) {
            return json.missingNode();
        }
        if (index >= segments.length) {
            return current;
        }
        String segment = segments[index];
        if (segment.endsWith("[]")) {
            String arrayField = segment.substring(0, segment.length() - 2);
            JsonNode array = arrayField.isBlank() ? current : current.path(arrayField);
            if (!array.isArray()) {
                return json.missingNode();
            }
            if (index == segments.length - 1) {
                return array;
            }
            ArrayNode projected = json.createArrayNode();
            for (JsonNode item : array) {
                JsonNode value = findPath(item, segments, index + 1);
                if (!exists(value)) {
                    continue;
                }
                if (value.isArray()) {
                    value.forEach(projected::add);
                } else {
                    projected.add(value);
                }
            }
            return projected;
        }
        if (current.isArray() && !segment.isEmpty()
                && segment.chars().allMatch(Character::isDigit)) {
            try {
                int arrayIndex = Integer.parseInt(segment);
                if (arrayIndex >= current.size()) {
                    return json.missingNode();
                }
                return findPath(current.path(arrayIndex), segments, index + 1);
            } catch (NumberFormatException exception) {
                return json.missingNode();
            }
        }
        return findPath(current.path(segment), segments, index + 1);
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

    private boolean isConditionGroup(JsonNode node) {
        return node != null && node.isObject() && (node.has("all") || node.has("any") || node.has("not"));
    }

    private boolean isClinicalOperator(String operator) {
        return switch (operator) {
            case "between", "not_between", "within_ref", "above_ref", "below_ref",
                 "is_missing", "is_critical", "is_stale", "unit_compare", "temporal", "derived" -> true;
            default -> false;
        };
    }

    private void requireFeatureEnabled(AuthoringFeatureFlag flag) {
        if (featureGate.enabled(flag)) {
            return;
        }
        throw new ApiException(
            ErrorCode.ENG_RULE_004,
            flag.displayName() + "能力开关未启用: " + SystemConfigService.runtimeFeatureFlagConfigKey(flag.key()));
    }

    private void requireObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + " 必须是 JSON 对象");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw invalid("规则 DSL 缺少字段: " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = optionalText(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Instant optionalInstant(JsonNode node, String... fields) {
        String value = firstText(node, fields);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw invalid("时间字段必须是 ISO-8601 Instant: " + value);
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_RULE_001, message);
    }

    private ApiException operatorInvalid(String message) {
        return new ApiException(ErrorCode.DSL_OPERATOR_INVALID, message);
    }

    private ApiException insufficientData(String message) {
        return new ApiException(ErrorCode.INSUFFICIENT_DATA, message);
    }

    private record LeafActual(String fact, String sourcePath, JsonNode actual, String source, String formula) {
    }

    private record ExpressionCollection(String arrayPath, List<ExpressionItem> items, int totalCount) {
    }

    private record ExpressionItem(JsonNode value, JsonNode rawItem, String source, Instant observedAt) {
    }

    private record ExpressionAggregate(JsonNode actual, String source) {
    }
}
