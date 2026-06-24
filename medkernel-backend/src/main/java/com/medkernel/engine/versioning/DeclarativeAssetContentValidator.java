package com.medkernel.engine.versioning;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.rule.ClinicalFunctionRegistry;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 独立声明式配置资产正文校验器。
 *
 * <p>值集、公式、医嘱套餐和动作卡没有其他领域表作为正文权威源，因此在登记版本前必须
 * 完成类型化结构校验并规范化 JSON。完整路径已有专用模型，不允许另存第二份通用正文。
 */
@Component
public class DeclarativeAssetContentValidator {

    private static final String SCHEMA_VERSION = "1.0";
    private static final int MAX_VALUE_SET_MEMBERS = 10_000;
    private static final Set<String> ORDER_ITEM_TYPES =
        Set.of("MEDICATION", "LAB", "IMAGING", "PROCEDURE", "NURSING");
    private static final Set<String> ACTION_TYPES =
        Set.of("NAVIGATE", "OPEN_FORM", "SUGGEST_ORDER", "ACKNOWLEDGE");
    private static final Set<String> ACTION_CODES =
        Set.of("INFO", "REMIND", "STRONG_REMINDER", "BLOCK", "SUGGEST_ORDER", "AUTO_DOCUMENT");
    private static final Set<String> SEVERITIES =
        Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> INDICATORS =
        Set.of("info", "warning", "critical");

    private final ObjectMapper json;

    public DeclarativeAssetContentValidator(ObjectMapper json) {
        this.json = json;
    }

    /**
     * 校验类型化正文并返回无格式噪声的规范 JSON。
     */
    public String validateAndCanonicalize(VersionedAssetType assetType, JsonNode content) {
        if (assetType == null) {
            throw invalid("资产类型不能为空");
        }
        if (content == null || !content.isObject()) {
            throw invalid(assetType + " 资产正文必须是 JSON 对象");
        }
        requireEquals(requiredText(content, "schemaVersion"), SCHEMA_VERSION, "schemaVersion");
        switch (assetType) {
            case VALUE_SET -> validateValueSet(content);
            case FORMULA -> validateFormula(content);
            case ORDER_SET -> validateOrderSet(content);
            case ACTION_CARD -> validateActionCard(content);
            case PATHWAY -> throw invalid("路径必须使用路径工作台维护，禁止登记第二份通用 JSON 正文");
            case FIELD_CATALOG -> throw invalid("字段目录必须使用字段目录工作台维护和发布");
            default -> throw invalid(assetType + " 不属于独立声明式配置资产");
        }
        try {
            return json.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, assetType + " 资产正文无法序列化", exception);
        }
    }

    private void validateValueSet(JsonNode root) {
        requiredText(root, "name");
        requiredText(root, "codeSystem");
        JsonNode members = requiredArray(root, "members");
        if (members.isEmpty()) {
            throw invalid("值集 members 不能为空");
        }
        if (members.size() > MAX_VALUE_SET_MEMBERS) {
            throw invalid("值集成员不能超过 " + MAX_VALUE_SET_MEMBERS + " 个");
        }
        Set<String> codes = new HashSet<>();
        for (JsonNode member : members) {
            requireObject(member, "值集成员");
            String code = requiredText(member, "code");
            requiredText(member, "display");
            if (!codes.add(code)) {
                throw invalid("值集成员编码重复：" + code);
            }
        }
    }

    private void validateFormula(JsonNode root) {
        requiredText(root, "name");
        String runtimeFunction = requiredText(root, "runtimeFunction");
        if (!ClinicalFunctionRegistry.isSupported(runtimeFunction)) {
            throw invalid("公式 runtimeFunction 不在受控允许范围：" + runtimeFunction);
        }
        JsonNode inputs = requiredArray(root, "inputs");
        if (inputs.isEmpty()) {
            throw invalid("公式 inputs 不能为空");
        }
        Set<String> inputNames = new HashSet<>();
        for (JsonNode input : inputs) {
            requireObject(input, "公式输入");
            String name = requiredText(input, "name");
            requiredText(input, "fieldPath");
            optionalText(input, "unit");
            if (!inputNames.add(name)) {
                throw invalid("公式输入名称重复：" + name);
            }
        }
        JsonNode output = requiredObject(root, "output");
        requireEquals(requiredText(output, "dataType"), "number", "公式输出 dataType");
        requiredText(output, "unit");
    }

    private void validateOrderSet(JsonNode root) {
        requiredText(root, "name");
        if (!root.path("requiresPhysicianConfirmation").isBoolean()
                || !root.path("requiresPhysicianConfirmation").asBoolean()) {
            throw invalid("医嘱套餐必须由医师确认，禁止自动开立医嘱");
        }
        JsonNode items = requiredArray(root, "items");
        if (items.isEmpty()) {
            throw invalid("医嘱套餐 items 不能为空");
        }
        Set<String> identities = new HashSet<>();
        for (JsonNode item : items) {
            requireObject(item, "医嘱套餐条目");
            String itemType = requiredText(item, "itemType");
            if (!ORDER_ITEM_TYPES.contains(itemType)) {
                throw invalid("医嘱套餐 itemType 不受支持：" + itemType);
            }
            String codeSystem = requiredText(item, "codeSystem");
            String code = requiredText(item, "code");
            requiredText(item, "display");
            requiredBoolean(item, "required");
            if (!identities.add(itemType + "|" + codeSystem + "|" + code)) {
                throw invalid("医嘱套餐条目重复：" + codeSystem + "|" + code);
            }
        }
    }

    private void validateActionCard(JsonNode root) {
        requiredText(root, "title");
        String actionCode = requiredText(root, "actionCode");
        if (!ACTION_CODES.contains(actionCode)) {
            throw invalid("动作卡 actionCode 不受支持：" + actionCode);
        }
        String severity = requiredText(root, "atSeverity");
        if (!SEVERITIES.contains(severity)) {
            throw invalid("动作卡 atSeverity 不受支持：" + severity);
        }
        String indicator = requiredText(root, "indicator");
        if (!INDICATORS.contains(indicator)) {
            throw invalid("动作卡 indicator 不受支持：" + indicator);
        }
        requiredText(root, "summary");
        requiredText(root, "detail");
        JsonNode source = requiredObject(root, "source");
        requiredText(source, "label");
        optionalText(source, "url");
        optionalText(source, "evidenceLevel");
        JsonNode suggestions = requiredArray(root, "suggestions");
        if (suggestions.isEmpty()) {
            throw invalid("动作卡 suggestions 不能为空");
        }
        boolean hasSuggestOrder = "SUGGEST_ORDER".equals(actionCode);
        for (JsonNode suggestion : suggestions) {
            requireObject(suggestion, "动作卡建议项");
            requiredText(suggestion, "label");
            String actionType = requiredText(suggestion, "actionType");
            if (!ACTION_TYPES.contains(actionType)) {
                throw invalid("动作卡 actionType 不受支持：" + actionType);
            }
            if (suggestion.has("payload") && !suggestion.path("payload").isObject()) {
                throw invalid("动作卡建议项 payload 必须是 JSON 对象");
            }
            hasSuggestOrder = hasSuggestOrder || "SUGGEST_ORDER".equals(actionType);
        }
        JsonNode overrideReasons = requiredArray(root, "overrideReasons");
        for (JsonNode reason : overrideReasons) {
            if (!reason.isTextual() || reason.asText().isBlank()) {
                throw invalid("动作卡 overrideReasons 仅允许非空文本");
            }
        }
        boolean confirmation = requiredBoolean(root, "requiresPhysicianConfirmation");
        if ((hasSuggestOrder || requiresPhysicianConfirmation(actionCode, severity)) && !confirmation) {
            throw invalid("动作卡建议医嘱必须由医师确认");
        }
    }

    private boolean requiresPhysicianConfirmation(String actionCode, String severity) {
        return "HIGH".equals(severity)
            || "CRITICAL".equals(severity)
            || "BLOCK".equals(actionCode)
            || "STRONG_REMINDER".equals(actionCode)
            || "SUGGEST_ORDER".equals(actionCode);
    }

    private JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isObject()) {
            throw invalid(field + " 必须是 JSON 对象");
        }
        return value;
    }

    private void requireObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + " 必须是 JSON 对象");
        }
    }

    private JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw invalid(field + " 必须是数组");
        }
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.asText().trim();
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(field + " 必须是字符串或 null");
        }
        return value.asText().isBlank() ? null : value.asText().trim();
    }

    private boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isBoolean()) {
            throw invalid(field + " 必须是布尔值");
        }
        return value.asBoolean();
    }

    private void requireEquals(String actual, String expected, String label) {
        if (!expected.equals(actual)) {
            throw invalid(label + " 仅支持 " + expected);
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
