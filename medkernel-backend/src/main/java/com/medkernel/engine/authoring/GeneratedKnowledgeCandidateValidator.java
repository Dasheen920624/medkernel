package com.medkernel.engine.authoring;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 大模型或模板生成知识候选的结构校验。
 *
 * <p>知识生成必须带来源锚点和领域建议；缺来源的内容不能进入可启用草稿。
 */
@Component
public class GeneratedKnowledgeCandidateValidator implements GeneratedAssetCandidateValidator {

    private static final String SCHEMA_VERSION = "1.0";

    private final ObjectMapper json;

    public GeneratedKnowledgeCandidateValidator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public VersionedAssetType assetType() {
        return VersionedAssetType.KNOWLEDGE;
    }

    @Override
    public GeneratedAssetValidation validate(String assetIdentity, JsonNode content) {
        requireSchema(content);
        requiredText(content, "domainSuggestion");
        requiredText(content, "title");
        requiredText(content, "summary");
        requiredText(content, "body");
        JsonNode sources = requiredArray(content, "sources");
        if (sources.isEmpty()) {
            throw invalid("知识生成 sources 不能为空，禁止无源生成");
        }
        for (JsonNode source : sources) {
            requireObject(source, "知识来源");
            requiredText(source, "sourceRef");
            requiredText(source, "anchorPath");
            requiredText(source, "authorityLevel");
        }
        return new GeneratedAssetValidation(canonical(content), List.of());
    }

    private void requireSchema(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw invalid("知识生成正文必须是 JSON 对象");
        }
        if (!SCHEMA_VERSION.equals(requiredText(content, "schemaVersion"))) {
            throw invalid("schemaVersion 仅支持 " + SCHEMA_VERSION);
        }
    }

    private JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw invalid(field + " 必须是数组");
        }
        return value;
    }

    private void requireObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + " 必须是 JSON 对象");
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.asText().trim();
    }

    private String canonical(JsonNode content) {
        try {
            return json.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "知识生成正文无法序列化", exception);
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
