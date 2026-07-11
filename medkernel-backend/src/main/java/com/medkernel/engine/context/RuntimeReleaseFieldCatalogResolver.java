package com.medkernel.engine.context;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.DeclarativeAssetRuntimePort;
import com.medkernel.engine.versioning.ResolvedDeclarativeAsset;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 按机构生效版本恢复不可变字段目录正文。
 */
@Component
public class RuntimeReleaseFieldCatalogResolver {

    private static final String RUNTIME_SOURCE = "RUNTIME_RELEASE";

    private final DeclarativeAssetRuntimePort assets;
    private final ObjectMapper json;

    public RuntimeReleaseFieldCatalogResolver(
            DeclarativeAssetRuntimePort assets,
            ObjectMapper json) {
        this.assets = assets == null ? DeclarativeAssetRuntimePort.unavailable() : assets;
        this.json = json;
    }

    public List<ContextFieldDescriptor> resolve(String tenantId, String runtimeReleaseId) {
        String tenant = required(tenantId, "租户");
        String releaseId = required(runtimeReleaseId, "机构生效版本 ID");
        ResolvedDeclarativeAsset asset = assets.resolve(
            tenant,
            releaseId,
            VersionedAssetType.FIELD_CATALOG,
            ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY
        ).orElseThrow(() -> new ApiException(
            ErrorCode.ENG_ASSET_002,
            "机构生效版本缺少字段目录资产：" + releaseId));
        return parse(releaseId, asset.contentJson());
    }

    private List<ContextFieldDescriptor> parse(String releaseId, String contentJson) {
        try {
            JsonNode root = json.readTree(contentJson);
            JsonNode fields = root.path("fields");
            if (!fields.isArray() || fields.size() == 0) {
                throw invalid(releaseId, "字段目录正文必须包含非空 fields 数组");
            }
            List<ContextFieldDescriptor> descriptors = new ArrayList<>();
            for (int index = 0; index < fields.size(); index++) {
                descriptors.add(field(releaseId, fields.get(index), index));
            }
            return List.copyOf(descriptors);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.ENG_ASSET_002,
                "机构生效版本字段目录正文不是合法 JSON：" + releaseId,
                exception);
        }
    }

    private ContextFieldDescriptor field(String releaseId, JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            throw invalid(releaseId, "字段目录 fields[" + index + "] 必须是对象");
        }
        String fieldPath = requiredText(releaseId, node, "fieldPath", index);
        String displayName = requiredText(releaseId, node, "displayName", index);
        return new ContextFieldDescriptor(
            requiredText(releaseId, node, "category", index),
            requiredText(releaseId, node, "group", index),
            requiredText(releaseId, node, "resourceType", index),
            fieldPath,
            displayName,
            requiredText(releaseId, node, "dataType", index),
            nullableText(node, "unit"),
            nullableText(node, "codeSystem"),
            ContextFieldDescriptionNormalizer.normalize(nullableText(node, "description"), displayName, fieldPath),
            RUNTIME_SOURCE,
            null,
            requiredBoolean(releaseId, node, "derived", index)
        );
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.ENG_ASSET_002, label + "不能为空");
        }
        return value.trim();
    }

    private String requiredText(String releaseId, JsonNode node, String field, int index) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(releaseId, "字段目录 fields[" + index + "]." + field + " 不能为空");
        }
        return value.asText().trim();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private boolean requiredBoolean(String releaseId, JsonNode node, String field, int index) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw invalid(releaseId, "字段目录 fields[" + index + "]." + field + " 必须是布尔值");
        }
        return value.asBoolean();
    }

    private ApiException invalid(String releaseId, String message) {
        return new ApiException(
            ErrorCode.ENG_ASSET_002,
            "机构生效版本字段目录正文不合法：" + releaseId + "，" + message);
    }
}
