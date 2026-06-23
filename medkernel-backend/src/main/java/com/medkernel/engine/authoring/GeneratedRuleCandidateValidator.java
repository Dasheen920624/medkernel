package com.medkernel.engine.authoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextFieldCatalogAssets;
import com.medkernel.engine.context.ContextFieldPathPolicy;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 自动生成规则候选的结构校验。
 *
 * <p>规则候选必须是完整 DSL、字段目录、系统字典和多触发绑定，不允许再携带手工运行版本。
 */
@Component
public class GeneratedRuleCandidateValidator implements GeneratedAssetCandidateValidator {

    private static final String SCHEMA_VERSION = "1.0";

    private final ObjectMapper json;

    public GeneratedRuleCandidateValidator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public VersionedAssetType assetType() {
        return VersionedAssetType.RULE;
    }

    @Override
    public GeneratedAssetValidation validate(String assetIdentity, JsonNode content) {
        requireSchema(content);
        String ruleCode = requiredText(content, "ruleCode");
        if (!ruleCode.equals(assetIdentity)) {
            throw invalid("规则稳定编码必须与资产身份一致");
        }
        requiredText(content, "name");
        JsonNode dsl = requiredObject(content, "dsl");
        requiredObject(dsl, "when");
        JsonNode then = requiredArray(dsl, "then");
        if (then.isEmpty()) {
            throw invalid("then 至少包含一个动作卡");
        }
        validateThenActions(then);
        requiredObject(dsl, "explain");
        rejectConditionFragments(dsl);
        String fieldCatalog = requiredText(content, "fieldCatalogIdentity");
        if (!ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY.equals(fieldCatalog)) {
            throw invalid("规则字段目录必须使用统一临床上下文字段目录资产 "
                + ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY);
        }
        List<String> fieldBindings = requireNonEmptyTextArray(content, "fieldBindings");
        rejectUnknownFields(fieldBindings);
        rejectUnknownFields(ContextFieldPathPolicy.ruleDslFields(dsl));
        List<String> terminologyRefs = requireNonEmptyTextArray(content, "terminologyRefs");
        validateTriggerBindings(content, "RULE_EXECUTION");

        Map<String, AssetDependencyDeclaration> dependencies = new LinkedHashMap<>();
        add(dependencies, VersionedAssetType.FIELD_CATALOG, fieldCatalog, AssetDependencyKind.FIELD);
        for (String terminologyRef : terminologyRefs) {
            add(dependencies, VersionedAssetType.TERMINOLOGY, terminologyRef, AssetDependencyKind.TERMINOLOGY);
        }
        for (String actionCardIdentity : findActionCardIdentities(dsl)) {
            add(dependencies, VersionedAssetType.ACTION_CARD, actionCardIdentity, AssetDependencyKind.OTHER);
        }
        return new GeneratedAssetValidation(canonical(content), new ArrayList<>(dependencies.values()));
    }

    private void rejectUnknownFields(List<String> fields) {
        List<String> unknown = ContextFieldPathPolicy.unknownFields(fields);
        if (!unknown.isEmpty()) {
            throw invalid("字段目录不存在：" + String.join(", ", unknown));
        }
    }

    private void validateTriggerBindings(JsonNode content, String requiredPurpose) {
        JsonNode triggers = requiredArray(content, "triggerBindings");
        if (triggers.isEmpty()) {
            throw invalid("规则 triggerBindings 不能为空");
        }
        for (JsonNode trigger : triggers) {
            requireObject(trigger, "触发绑定");
            requiredText(trigger, "triggerPoint");
            String purpose = requiredText(trigger, "purpose");
            if (!requiredPurpose.equals(purpose)) {
                throw invalid("规则触发用途必须为 " + requiredPurpose);
            }
        }
    }

    private List<String> findActionCardIdentities(JsonNode node) {
        List<String> identities = new ArrayList<>();
        collectActionCards(node, identities);
        return identities;
    }

    private void collectActionCards(JsonNode node, List<String> identities) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode actionCardRef = node.get("actionCardRef");
            if (actionCardRef != null && actionCardRef.isTextual()
                    && actionCardRef.asText().startsWith("ACTION.")) {
                identities.add(actionCardRef.asText().trim());
            }
            node.fields().forEachRemaining(entry -> collectActionCards(entry.getValue(), identities));
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectActionCards(item, identities));
        }
    }

    private void validateThenActions(JsonNode then) {
        for (JsonNode action : then) {
            requireObject(action, "规则动作卡");
            if (text(action, "actionCardRef") == null && text(action, "actionCode") == null) {
                throw invalid("规则 then 动作必须包含 actionCardRef 或完整 actionCode");
            }
        }
    }

    private void rejectConditionFragments(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                if ("conditionFragments".equals(field)
                        || "conditionFragmentRef".equals(field)
                        || "fragmentRef".equals(field)) {
                    throw invalid("规则 DSL 已支持任意层级条件树，禁止引用条件片段库");
                }
                rejectConditionFragments(entry.getValue());
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(this::rejectConditionFragments);
        }
    }

    private void add(
            Map<String, AssetDependencyDeclaration> dependencies,
            VersionedAssetType type,
            String identity,
            AssetDependencyKind kind) {
        dependencies.putIfAbsent(type + "|" + identity + "|" + kind,
            new AssetDependencyDeclaration(type, identity, null, null, kind));
    }

    private void requireSchema(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw invalid("规则生成正文必须是 JSON 对象");
        }
        if (!SCHEMA_VERSION.equals(requiredText(content, "schemaVersion"))) {
            throw invalid("schemaVersion 仅支持 " + SCHEMA_VERSION);
        }
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

    private List<String> requireNonEmptyTextArray(JsonNode node, String field) {
        JsonNode array = requiredArray(node, field);
        if (array.isEmpty()) {
            throw invalid(field + " 不能为空");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw invalid(field + " 只能包含非空字符串");
            }
            values.add(item.asText().trim());
        }
        return values;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.asText().trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private String canonical(JsonNode content) {
        try {
            return json.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "规则生成正文无法序列化", exception);
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
