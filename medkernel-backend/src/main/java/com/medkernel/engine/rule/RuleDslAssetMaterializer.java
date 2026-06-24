package com.medkernel.engine.rule;

import java.util.Iterator;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.versioning.DeclarativeAssetRuntimePort;
import com.medkernel.engine.versioning.RemovedRuntimeSelectorFields;
import com.medkernel.engine.versioning.ResolvedDeclarativeAsset;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 将规则 DSL 中的值集和公式引用物化为当前机构生效版本锁定的不可变内容。
 *
 * <p>编著正文只保存精确引用，禁止复制值集成员或把可变公式实现写进规则；执行器仍只接收
 * 确定性、无外部查询的完整 DSL，因此一次执行全程使用同一机构生效版本快照。
 */
@Component
public class RuleDslAssetMaterializer {

    private final ObjectMapper json;
    private final DeclarativeAssetRuntimePort assets;

    public RuleDslAssetMaterializer(
            ObjectMapper json,
            DeclarativeAssetRuntimePort assets) {
        this.json = json;
        this.assets = assets == null ? DeclarativeAssetRuntimePort.unavailable() : assets;
    }

    public JsonNode materialize(String tenantId, String runtimeReleaseId, JsonNode dsl) {
        if (dsl == null || !dsl.isObject()) {
            throw invalid("规则 DSL 必须是 JSON 对象");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw invalid("规则资产物化缺少租户");
        }
        if (runtimeReleaseId == null || runtimeReleaseId.isBlank()) {
            throw invalid("规则资产物化缺少机构生效版本 ID");
        }
        JsonNode copy = dsl.deepCopy();
        visit(copy, tenantId.trim(), runtimeReleaseId.trim());
        return copy;
    }

    private void visit(JsonNode node, String tenantId, String runtimeReleaseId) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                visit(item, tenantId, runtimeReleaseId);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        if (RemovedRuntimeSelectorFields.hasAny(object, RemovedRuntimeSelectorFields.versionFields())) {
            throw invalid("规则资产引用不得手工携带版本定位，由机构生效版本统一锁定版本");
        }
        String valueSet = text(object, "valueSet");
        if (valueSet != null) {
            materializeValueSet(object, tenantId, runtimeReleaseId, valueSet);
        }
        String formula = text(object, "formula");
        if (formula != null) {
            materializeFormula(object, tenantId, runtimeReleaseId, formula);
        }
        String actionCardRef = text(object, "actionCardRef");
        if (actionCardRef != null) {
            materializeActionCard(object, tenantId, runtimeReleaseId, actionCardRef);
        }
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            visit(fields.next().getValue(), tenantId, runtimeReleaseId);
        }
    }

    private void materializeValueSet(
            ObjectNode target,
            String tenantId,
            String runtimeReleaseId,
            String identity) {
        ResolvedDeclarativeAsset resolved = resolve(
            tenantId, runtimeReleaseId, VersionedAssetType.VALUE_SET, identity, "值集");
        JsonNode content = parseContent(resolved);
        JsonNode members = content.path("members");
        if (!members.isArray()) {
            throw invalid("值集正文缺少 members：" + identity);
        }
        ArrayNode codes = json.createArrayNode();
        for (JsonNode member : members) {
            String code = text(member, "code");
            if (code == null) {
                throw invalid("值集成员缺少 code：" + identity);
            }
            codes.add(code);
        }
        target.set("members", codes);
        target.put("expandedCount", codes.size());
        target.put("resolvedAssetVersion", resolved.assetVersion());
    }

    private void materializeFormula(
            ObjectNode target,
            String tenantId,
            String runtimeReleaseId,
            String identity) {
        ResolvedDeclarativeAsset resolved = resolve(
            tenantId, runtimeReleaseId, VersionedAssetType.FORMULA, identity, "公式");
        JsonNode content = parseContent(resolved);
        String runtimeFunction = text(content, "runtimeFunction");
        if (runtimeFunction == null || !ClinicalFunctionRegistry.isSupported(runtimeFunction)) {
            throw invalid("公式资产未绑定受控运行函数：" + identity);
        }
        target.put("formulaAsset", identity);
        target.put("formula", runtimeFunction);
        target.put("resolvedAssetVersion", resolved.assetVersion());
    }

    private void materializeActionCard(
            ObjectNode target,
            String tenantId,
            String runtimeReleaseId,
            String identity) {
        ResolvedDeclarativeAsset resolved = resolve(
            tenantId, runtimeReleaseId, VersionedAssetType.ACTION_CARD, identity, "动作卡");
        JsonNode content = parseContent(resolved);
        copyRequired(content, target, "actionCode", identity);
        copyRequired(content, target, "atSeverity", identity);
        copyRequired(content, target, "indicator", identity);
        copyRequired(content, target, "summary", identity);
        copyRequired(content, target, "detail", identity);
        copyRequired(content, target, "source", identity);
        copyRequired(content, target, "suggestions", identity);
        copyRequired(content, target, "overrideReasons", identity);
        copyRequired(content, target, "requiresPhysicianConfirmation", identity);
        target.put("resolvedActionCardVersion", resolved.assetVersion());
        target.put("resolvedActionCardHash", resolved.contentHash());
    }

    private void copyRequired(JsonNode content, ObjectNode target, String field, String identity) {
        JsonNode value = content.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            throw invalid("动作卡正文缺少 " + field + "：" + identity);
        }
        target.set(field, value.deepCopy());
    }

    private ResolvedDeclarativeAsset resolve(
            String tenantId,
            String runtimeReleaseId,
            VersionedAssetType type,
            String identity,
            String label) {
        return assets.resolve(tenantId, runtimeReleaseId, type, identity)
            .orElseThrow(() -> invalid(
                "当前机构生效版本未解析到" + label + "：" + identity + "@" + runtimeReleaseId
            ));
    }

    private JsonNode parseContent(ResolvedDeclarativeAsset asset) {
        try {
            JsonNode content = json.readTree(asset.contentJson());
            if (content == null || !content.isObject()) {
                throw invalid(asset.assetType() + " 资产正文必须是 JSON 对象");
            }
            return content;
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.ENG_RULE_001,
                asset.assetType() + " 资产正文不是合法 JSON：" + asset.assetIdentity(),
                exception
            );
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_RULE_001, message);
    }
}
