package com.medkernel.engine.authoring;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 规则条件树与路径守卫的权威中文预览服务。
 */
@Service
public class AuthoringPreviewService {

    private static final Map<String, String> OPERATOR_LABELS = Map.ofEntries(
        Map.entry("exists", "存在"),
        Map.entry("equals", "等于"),
        Map.entry("not_equals", "不等于"),
        Map.entry("contains", "包含"),
        Map.entry("gt", "大于"),
        Map.entry("gte", "大于等于"),
        Map.entry("lt", "小于"),
        Map.entry("lte", "小于等于"),
        Map.entry("in", "属于集合"),
        Map.entry("not_in", "不属于集合"),
        Map.entry("between", "位于区间"),
        Map.entry("not_between", "不在区间"),
        Map.entry("within_ref", "位于参考范围内"),
        Map.entry("above_ref", "高于参考上限"),
        Map.entry("below_ref", "低于参考下限"),
        Map.entry("is_missing", "缺少临床数据"),
        Map.entry("is_critical", "为危急值"),
        Map.entry("is_stale", "结果陈旧"),
        Map.entry("unit_compare", "单位换算后比较"),
        Map.entry("temporal", "满足时间窗条件"),
        Map.entry("derived", "受控公式")
    );

    private static final Map<String, String> SELECT_LABELS = Map.of(
        "latest", "最近一次",
        "first", "最早一次",
        "max", "最大值",
        "min", "最小值",
        "avg", "平均值",
        "sum", "求和",
        "count", "计数"
    );

    private static final Map<String, String> FORMULA_LABELS = Map.of(
        "CKD_EPI_2021_EGFR", "eGFR CKD-EPI 2021",
        "COCKCROFT_GAULT_CRCL", "CrCl Cockcroft-Gault",
        "MOSTELLER_BSA", "BSA Mosteller",
        "BMI", "BMI 体重指数"
    );

    private static final Map<String, String> ACTION_LABELS = Map.of(
        "BLOCK", "阻断",
        "STRONG_REMINDER", "强提醒",
        "REMIND", "提醒",
        "INFO", "提示",
        "SUGGEST_ORDER", "建议医嘱",
        "AUTO_DOCUMENT", "自动留痕"
    );

    @SuppressWarnings("unused")
    private final ObjectMapper json;

    public AuthoringPreviewService(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    public AuthoringPreviewResponse preview(AuthoringPreviewRequest request) {
        if (request == null) {
            throw invalid("预览请求不能为空");
        }
        if (request.subject() == null) {
            throw invalid("预览对象 subject 不能为空");
        }
        JsonNode dsl = request.dsl();
        if (dsl == null || dsl.isNull() || dsl.isMissingNode()) {
            throw invalid("预览 DSL 不能为空");
        }
        if (!dsl.isObject()) {
            throw invalid("预览 DSL 必须是 JSON 对象");
        }

        List<String> warnings = new ArrayList<>();
        List<AuthoringPreviewSegment> segments = new ArrayList<>();
        JsonNode condition = conditionNode(request.subject(), dsl);
        String conditionText = renderNode(condition, warnings, segments, conditionPath(request.subject(), dsl));

        List<String> lines = new ArrayList<>();
        if (request.subject() == AuthoringPreviewSubject.PATHWAY_GUARD) {
            lines.add(renderPathwayGuardHeading(dsl) + conditionText);
        } else {
            lines.add("当 " + conditionText);
        }
        String actions = renderActions(dsl);
        if (hasText(actions)) {
            lines.add("动作：" + actions);
        }
        String source = renderSource(dsl);
        if (hasText(source)) {
            lines.add("来源：" + source);
        }

        return new AuthoringPreviewResponse(
            String.join("；", lines) + "。",
            lines,
            segments,
            warnings,
            request.traceId()
        );
    }

    private JsonNode conditionNode(AuthoringPreviewSubject subject, JsonNode dsl) {
        if (subject == AuthoringPreviewSubject.PATHWAY_GUARD && dsl.has("guard")) {
            return requireConditionNode(dsl.get("guard"), "路径守卫 guard");
        }
        if (subject == AuthoringPreviewSubject.RULE_CONDITION && dsl.has("when")) {
            return requireConditionNode(dsl.get("when"), "规则条件 when");
        }
        return requireConditionNode(dsl, "条件 DSL");
    }

    private String conditionPath(AuthoringPreviewSubject subject, JsonNode dsl) {
        if (subject == AuthoringPreviewSubject.PATHWAY_GUARD && dsl.has("guard")) {
            return "$.guard";
        }
        if (subject == AuthoringPreviewSubject.RULE_CONDITION && dsl.has("when")) {
            return "$.when";
        }
        return "$";
    }

    private JsonNode requireConditionNode(JsonNode node, String label) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            throw invalid(label + " 必须是 JSON 对象");
        }
        return node;
    }

    private String renderNode(
        JsonNode node,
        List<String> warnings,
        List<AuthoringPreviewSegment> segments,
        String path
    ) {
        if (node.has("all") && node.get("all").isArray()) {
            return renderGroup("且", node.get("all"), warnings, segments, path + ".all");
        }
        if (node.has("any") && node.get("any").isArray()) {
            return renderGroup("或", node.get("any"), warnings, segments, path + ".any");
        }
        if (node.has("not")) {
            JsonNode not = requireConditionNode(node.get("not"), "not 条件");
            return "不满足（" + renderNode(not, warnings, segments, path + ".not") + "）";
        }
        if (node.has("operator") && (node.has("fact") || node.has("expr"))) {
            String text = renderLeaf(node, warnings);
            segments.add(new AuthoringPreviewSegment("condition", path, text));
            return text;
        }
        warnings.add("存在无法解释的条件节点：" + path);
        return "无法解释的条件节点";
    }

    private String renderGroup(
        String joiner,
        JsonNode children,
        List<String> warnings,
        List<AuthoringPreviewSegment> segments,
        String path
    ) {
        if (children.isEmpty()) {
            warnings.add(path + " 条件组为空");
            return "空条件组";
        }
        List<String> rendered = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            rendered.add(renderNode(children.get(i), warnings, segments, path + "[" + i + "]"));
        }
        String text = String.join(" " + joiner + " ", rendered);
        return rendered.size() > 1 ? "（" + text + "）" : text;
    }

    private String renderLeaf(JsonNode node, List<String> warnings) {
        String operator = optionalText(node, "operator");
        if (!hasText(operator)) {
            throw invalid("条件叶子缺少 operator");
        }
        operator = operator.toLowerCase(Locale.ROOT);
        String label = renderLeafLabel(node, warnings);
        JsonNode value = node.get("value");
        if ("derived".equals(operator)) {
            return renderDerivedLabel(node, label, warnings) + " 计算 " + renderValue(value, warnings);
        }
        String operatorLabel = OPERATOR_LABELS.getOrDefault(operator, operator);
        if (operatorNeedsValue(operator)) {
            return label + " " + operatorLabel + " " + renderValue(value, warnings);
        }
        return label + " " + operatorLabel;
    }

    private String renderLeafLabel(JsonNode node, List<String> warnings) {
        String uiLabel = optionalText(node.path("ui"), "label");
        if (hasText(uiLabel) && !uiLabel.startsWith("条件 ")) {
            return uiLabel;
        }
        JsonNode expr = node.get("expr");
        if (expr != null && expr.isObject()) {
            return renderExpression(expr, warnings);
        }
        String fact = optionalText(node, "fact");
        if (hasText(fact)) {
            return fact;
        }
        throw invalid("条件叶子缺少 fact 或 expr");
    }

    private String renderDerivedLabel(JsonNode node, String label, List<String> warnings) {
        JsonNode expr = node.get("expr");
        if (expr == null || !expr.isObject()) {
            return label;
        }
        String expression = renderExpression(expr, warnings);
        if (!hasText(expression) || expression.equals(label)) {
            return label;
        }
        return label + "（" + expression + "）";
    }

    private String renderExpression(JsonNode expr, List<String> warnings) {
        String field = optionalText(expr, "field");
        if (!hasText(field)) {
            throw invalid("表达式 expr.field 不能为空");
        }
        String select = optionalText(expr, "select");
        String label = SELECT_LABELS.getOrDefault(
            select == null ? "field" : select.toLowerCase(Locale.ROOT),
            select == null ? "字段" : select);
        StringBuilder builder = new StringBuilder(label).append(' ').append(field);
        JsonNode where = expr.get("where");
        List<String> parts = new ArrayList<>();
        if (where != null && where.isObject()) {
            parts.add("过滤：" + renderNode(where, warnings, new ArrayList<>(), "$.expr.where"));
        }
        String over = optionalText(expr, "over");
        if (hasText(over)) {
            parts.add("窗口 " + over);
        }
        String referenceTime = optionalText(expr, "referenceTime");
        if (hasText(referenceTime)) {
            parts.add("基准时间 " + referenceTime);
        }
        if (!parts.isEmpty()) {
            builder.append("（").append(String.join("，", parts)).append("）");
        }
        return builder.toString();
    }

    private String renderValue(JsonNode value, List<String> warnings) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "空值";
        }
        if (value.isObject()) {
            if (value.has("const")) {
                return renderValue(value.get("const"), warnings);
            }
            if (value.has("field")) {
                return "字段 " + textValue(value.get("field"));
            }
            if (value.has("valueSet")) {
                return renderValueSet(value);
            }
            if (value.has("formula")) {
                return renderFormula(value);
            }
            if (value.has("min") || value.has("max")) {
                return renderRange(value);
            }
            if (value.has("mode") || value.has("window") || value.has("condition")) {
                return renderTemporal(value, warnings);
            }
            if (value.has("maxAge") || value.has("referenceTime")) {
                return renderStaleness(value);
            }
            warnings.add("存在未命名的结构化比较值：" + value);
            return value.toString();
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(textValue(item)));
            return String.join("、", values);
        }
        return textValue(value);
    }

    private String renderValueSet(JsonNode value) {
        StringBuilder builder = new StringBuilder("值集 ").append(textValue(value.get("valueSet")));
        List<String> details = new ArrayList<>();
        String packageVersion = optionalText(value, "packageVersion");
        if (hasText(packageVersion)) {
            details.add("包版本 " + packageVersion);
        }
        JsonNode members = value.get("members");
        if (members != null && members.isArray() && !members.isEmpty()) {
            List<String> rendered = new ArrayList<>();
            members.forEach(member -> rendered.add(textValue(member)));
            details.add("成员 " + String.join("、", rendered));
        }
        if (!details.isEmpty()) {
            builder.append("（").append(String.join("，", details)).append("）");
        }
        return builder.toString();
    }

    private String renderFormula(JsonNode value) {
        String formula = textValue(value.get("formula"));
        StringBuilder builder = new StringBuilder(FORMULA_LABELS.getOrDefault(formula, formula));
        JsonNode parameters = value.get("parameters");
        if (parameters != null && parameters.isObject() && !parameters.isEmpty()) {
            StringJoiner joiner = new StringJoiner("，");
            Iterator<Map.Entry<String, JsonNode>> fields = parameters.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                joiner.add(entry.getKey() + "=" + textValue(entry.getValue()));
            }
            builder.append("（参数 ").append(joiner).append("）");
        }
        String source = optionalText(value, "source");
        if (hasText(source)) {
            builder.append("，来源 ").append(source);
        }
        return builder.toString();
    }

    private String renderRange(JsonNode value) {
        List<String> parts = new ArrayList<>();
        if (value.has("min")) {
            parts.add("下限 " + textValue(value.get("min")));
        }
        if (value.has("max")) {
            parts.add("上限 " + textValue(value.get("max")));
        }
        String unit = optionalText(value, "unit");
        if (hasText(unit)) {
            parts.add("单位 " + unit);
        }
        return String.join("，", parts);
    }

    private String renderTemporal(JsonNode value, List<String> warnings) {
        List<String> parts = new ArrayList<>();
        String mode = optionalText(value, "mode");
        if (hasText(mode)) {
            parts.add("模式 " + mode);
        }
        String window = optionalText(value, "window");
        if (hasText(window)) {
            parts.add("窗口 " + window);
        }
        JsonNode condition = value.get("condition");
        if (condition != null && condition.isObject()) {
            parts.add("条件 " + renderNode(condition, warnings, new ArrayList<>(), "$.value.condition"));
        }
        return String.join("，", parts);
    }

    private String renderStaleness(JsonNode value) {
        List<String> parts = new ArrayList<>();
        String maxAge = optionalText(value, "maxAge");
        if (hasText(maxAge)) {
            parts.add("最长时效 " + maxAge);
        }
        String referenceTime = optionalText(value, "referenceTime");
        if (hasText(referenceTime)) {
            parts.add("基准时间 " + referenceTime);
        }
        return String.join("，", parts);
    }

    private String renderActions(JsonNode dsl) {
        JsonNode then = dsl.get("then");
        if (then == null || !then.isArray() || then.isEmpty()) {
            return null;
        }
        List<String> actions = new ArrayList<>();
        for (JsonNode action : then) {
            if (!action.isObject()) {
                continue;
            }
            String code = optionalText(action, "actionCode");
            String summary = optionalText(action, "summary");
            String label = ACTION_LABELS.getOrDefault(code == null ? "" : code, code);
            if (hasText(summary)) {
                actions.add(hasText(label) ? label + "：" + summary : summary);
            } else if (hasText(label)) {
                actions.add(label);
            }
        }
        return actions.isEmpty() ? null : String.join("；", actions);
    }

    private String renderSource(JsonNode dsl) {
        JsonNode source = sourceNode(dsl);
        if (source == null || source.isMissingNode() || source.isNull()) {
            return null;
        }
        if (source.isTextual()) {
            return source.asText();
        }
        if (!source.isObject()) {
            return source.toString();
        }
        String label = firstText(source, "label", "name", "title", "ref");
        String evidenceLevel = firstText(source, "evidenceLevel", "evidence_level", "grade");
        List<String> parts = new ArrayList<>();
        if (hasText(label)) {
            parts.add(label);
        }
        if (hasText(evidenceLevel)) {
            parts.add("证据等级 " + evidenceLevel);
        }
        return parts.isEmpty() ? source.toString() : String.join("，", parts);
    }

    private JsonNode sourceNode(JsonNode dsl) {
        JsonNode explainSource = dsl.path("explain").path("source");
        if (!explainSource.isMissingNode() && !explainSource.isNull()) {
            return explainSource;
        }
        JsonNode then = dsl.get("then");
        if (then != null && then.isArray()) {
            for (JsonNode action : then) {
                JsonNode actionSource = action.path("source");
                if (!actionSource.isMissingNode() && !actionSource.isNull()) {
                    return actionSource;
                }
            }
        }
        return null;
    }

    private String renderPathwayGuardHeading(JsonNode dsl) {
        String edgeCode = optionalText(dsl, "edgeCode");
        String from = optionalText(dsl, "fromNodeCode");
        String to = optionalText(dsl, "toNodeCode");
        StringBuilder builder = new StringBuilder("路径守卫");
        if (hasText(edgeCode)) {
            builder.append(' ').append(edgeCode);
        }
        if (hasText(from) || hasText(to)) {
            builder.append("（从 ")
                .append(hasText(from) ? from : "未指定")
                .append(" 到 ")
                .append(hasText(to) ? to : "未指定")
                .append("）");
        }
        builder.append("：");
        return builder.toString();
    }

    private boolean operatorNeedsValue(String operator) {
        return switch (operator) {
            case "exists", "within_ref", "above_ref", "below_ref", "is_missing" -> false;
            default -> true;
        };
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = optionalText(node, field);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String optionalText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return textValue(value);
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue().toString();
        }
        if (node.isBoolean()) {
            return Boolean.toString(node.asBoolean());
        }
        return node.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
