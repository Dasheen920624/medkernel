package com.medkernel.engine.knowledge.delivery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 医疗资源包内部统一使用的确定性 JSON 编解码器。
 *
 * <p>对象键按 Unicode 字典序递归排序，数组保持业务顺序，输出固定为无缩进 UTF-8 JSON；
 * 读取时拒绝未知字段和非规范字节，避免宿主配置、时间或数据库坐标混入内容寻址输入。
 */
final class CanonicalJson {

    private final ObjectMapper json;

    CanonicalJson(ObjectMapper source) {
        this.json = source.copy()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(SerializationFeature.INDENT_OUTPUT, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    byte[] encode(Object value) {
        try {
            return json.writeValueAsBytes(sort(json.valueToTree(value)));
        } catch (JsonProcessingException exception) {
            throw invalid("医疗资源包 JSON 无法规范化编码", exception);
        }
    }

    <T> T decodeCanonical(byte[] bytes, Class<T> type) {
        if (bytes == null || bytes.length == 0) {
            throw invalid("医疗资源包 JSON 字节不能为空", null);
        }
        try {
            T decoded = json.readValue(bytes, type);
            byte[] canonical = encode(decoded);
            if (!Arrays.equals(bytes, canonical)) {
                throw invalid("医疗资源包 JSON 不是规范化字节", null);
            }
            return decoded;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid("医疗资源包 JSON 无法解析或包含未知字段", exception);
        }
    }

    JsonNode normalize(JsonNode value) {
        return sort(value == null ? json.nullNode() : value);
    }

    private JsonNode sort(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value;
        }
        if (value.isArray()) {
            ArrayNode result = json.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        ObjectNode result = json.createObjectNode();
        List<String> names = new ArrayList<>();
        value.fieldNames().forEachRemaining(names::add);
        names.stream().sorted().forEach(name -> result.set(name, sort(value.get(name))));
        return result;
    }

    private static ApiException invalid(String message, Throwable cause) {
        return cause == null
            ? new ApiException(ErrorCode.VALIDATION_FAILED, message)
            : new ApiException(ErrorCode.VALIDATION_FAILED, message, cause);
    }
}
