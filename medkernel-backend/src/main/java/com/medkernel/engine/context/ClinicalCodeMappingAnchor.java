package com.medkernel.engine.context;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 标准临床对象编码字段的字典映射锚点。
 *
 * <p>它不直接判定映射是否正确，只把本地编码、来源记录和目标字典域固定下来，
 * 供术语映射生成候选、冲突和机构生效版本影响证据时追踪。
 */
public record ClinicalCodeMappingAnchor(
    @NotNull CanonicalResourceType resourceType,
    @NotBlank String resourceId,
    @NotBlank String fieldName,
    @NotBlank String localCode,
    String localCodeSystem,
    String displayName,
    @NotBlank String targetDictionaryKey,
    String sourceSystem,
    String sourceRecordId,
    String mappedVersion
) {
    public ClinicalCodeMappingAnchor {
        resourceId = requireText(resourceId, "resourceId");
        fieldName = requireText(fieldName, "fieldName");
        localCode = requireText(localCode, "localCode");
        targetDictionaryKey = requireText(targetDictionaryKey, "targetDictionaryKey");
        if (resourceType == null) {
            throw new IllegalArgumentException("resourceType 不能为空");
        }
    }

    public String key() {
        return resourceType.name() + ":" + resourceId + ":" + fieldName + ":" + localCode;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
