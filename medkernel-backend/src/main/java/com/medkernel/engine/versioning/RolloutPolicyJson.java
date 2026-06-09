package com.medkernel.engine.versioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 发布策略 JSON 编解码。
 */
final class RolloutPolicyJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RolloutPolicyJson() {
    }

    static String encode(RolloutPolicy policy) {
        try {
            return MAPPER.writeValueAsString(policy);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("发布策略序列化失败", exception);
        }
    }

    static RolloutPolicy decode(String json) {
        try {
            return MAPPER.readValue(json, RolloutPolicy.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("发布策略反序列化失败", exception);
        }
    }
}
