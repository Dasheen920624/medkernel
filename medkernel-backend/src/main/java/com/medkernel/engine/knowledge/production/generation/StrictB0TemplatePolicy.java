package com.medkernel.engine.knowledge.production.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 严格 B0 非模型待编著骨架识别策略。
 *
 * <p>这是 {@link SourceCandidateGenerator} 生成契约的唯一识别入口。只有根字段、待编著章节标记和来源证据
 * 完全符合确定性 B0 契约时才返回 {@code true}；模型标记、额外字段、已编著内容或无效来源证据均不属于
 * B0 启动骨架，必须继续执行完整临床红线与模型影子评测门禁。
 */
@Component
public class StrictB0TemplatePolicy {

    private static final String B0_GENERATION_MODE = "B0_TEMPLATE";
    private static final String PENDING_AUTHORING = "PENDING_AUTHORING";
    private static final String PENDING_SECTION_PATTERN = "^待编著（结构：[^（）\\r\\n]+）$";
    private static final String SHA256_HEX = "^[0-9a-f]{64}$";
    private static final Set<String> B0_ROOT_FIELDS = Set.of(
        "generationMode",
        "medicalContentStatus",
        "generatedByModel",
        "template",
        "sections",
        "sourceEvidence");
    private static final Set<String> B0_SOURCE_EVIDENCE_FIELDS = Set.of(
        "anchorPath",
        "excerpt",
        "contentHash");

    private final ObjectMapper objectMapper;

    public StrictB0TemplatePolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 判断候选 payload 是否为严格 B0 非模型待编著骨架。
     *
     * @param payload 候选 JSON payload
     * @return 完全符合 B0 生成契约时返回 true
     */
    public boolean matches(String payload) {
        return matches(parse(payload));
    }

    /**
     * 判断已解析候选 payload 是否为严格 B0 非模型待编著骨架。
     *
     * @param root 已解析候选 JSON
     * @return 完全符合 B0 生成契约时返回 true
     */
    public boolean matches(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        List<String> fieldNames = new ArrayList<>();
        root.fieldNames().forEachRemaining(fieldNames::add);
        if (!B0_ROOT_FIELDS.containsAll(fieldNames) || fieldNames.size() != B0_ROOT_FIELDS.size()) {
            return false;
        }
        if (!B0_GENERATION_MODE.equals(root.path("generationMode").asText())
                || !PENDING_AUTHORING.equals(root.path("medicalContentStatus").asText())
                || !root.has("generatedByModel")
                || !root.path("generatedByModel").isBoolean()
                || root.path("generatedByModel").asBoolean()
                || root.path("template").asText("").isBlank()) {
            return false;
        }
        JsonNode sections = root.path("sections");
        if (!sections.isObject() || sections.isEmpty()) {
            return false;
        }
        List<JsonNode> sectionValues = new ArrayList<>();
        sections.elements().forEachRemaining(sectionValues::add);
        if (sectionValues.stream().anyMatch(value ->
                !value.isTextual() || !value.asText().matches(PENDING_SECTION_PATTERN))) {
            return false;
        }
        JsonNode evidence = root.path("sourceEvidence");
        if (!evidence.isArray() || evidence.isEmpty()) {
            return false;
        }
        for (JsonNode item : evidence) {
            if (!item.isObject()) {
                return false;
            }
            List<String> evidenceFieldNames = new ArrayList<>();
            item.fieldNames().forEachRemaining(evidenceFieldNames::add);
            if (!B0_SOURCE_EVIDENCE_FIELDS.containsAll(evidenceFieldNames)
                    || evidenceFieldNames.size() != B0_SOURCE_EVIDENCE_FIELDS.size()
                    || item.path("anchorPath").asText("").isBlank()
                    || item.path("excerpt").asText("").isBlank()
                    || !item.path("contentHash").asText("").matches(SHA256_HEX)) {
                return false;
            }
        }
        return true;
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException invalidJson) {
            return null;
        }
    }
}
