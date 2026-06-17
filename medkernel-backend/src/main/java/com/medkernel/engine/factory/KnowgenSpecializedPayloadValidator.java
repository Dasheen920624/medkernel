package com.medkernel.engine.factory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * KNOWGEN 专用 payload 结构校验器。
 *
 * <p>校验必备结构是否存在，并阻断任何声明已预填医学内容的 payload。真实医学值必须来自后续真实来源导入。
 */
@Service
public class KnowgenSpecializedPayloadValidator {

    private final KnowgenSpecializedAssetSkeletonRegistry registry;
    private final ObjectMapper json;

    public KnowgenSpecializedPayloadValidator(KnowgenSpecializedAssetSkeletonRegistry registry, ObjectMapper json) {
        this.registry = registry;
        this.json = json;
    }

    /** 校验指定 KNOWGEN 卡的专用 payload 结构。 */
    public void validate(String cardCode, String payload) {
        KnowgenSpecializedAssetSkeleton skeleton = registry.require(cardCode);
        List<String> violations = new ArrayList<>();
        JsonNode root = parse(payload);
        if (root.path("clinicalContentSeeded").asBoolean(false)) {
            violations.add("clinicalContentSeeded 必须为 false，禁止预填真实医学内容");
        }
        JsonNode sections = root.path("sections");
        if (!sections.isObject()) {
            violations.add("sections 必须为对象");
        } else {
            for (String field : skeleton.requiredPayloadFields()) {
                JsonNode value = sections.path(field);
                if (isEmptyStructure(value)) {
                    violations.add("缺少必备结构字段: " + field);
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                "KNOWGEN 专用 payload 校验不合格：" + String.join("；", violations));
        }
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "KNOWGEN 专用 payload 不能为空");
        }
        try {
            return json.readTree(payload);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "KNOWGEN 专用 payload 不是合法 JSON");
        }
    }

    private boolean isEmptyStructure(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return true;
        }
        if (value.isTextual()) {
            return value.asText("").isBlank();
        }
        if (value.isArray() || value.isObject()) {
            return value.isEmpty();
        }
        return false;
    }
}
