package com.medkernel.engine.terminology;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 术语映射不可变快照的统一 JSON 编解码器。
 */
public final class TermMappingSnapshotCodec {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private TermMappingSnapshotCodec() {
    }

    public static String write(TermMappingSnapshot snapshot) {
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "术语映射快照序列化失败");
        }
    }

    public static TermMappingSnapshot read(String content) {
        try {
            return JSON.readValue(content, TermMappingSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "术语映射快照结构不合法");
        }
    }
}
