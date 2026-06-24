package com.medkernel.engine.versioning;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 已移除的运行定位输入字段。
 */
public final class RemovedRuntimeSelectorFields {

    public static final String PACKAGE_ID = "packageId";
    public static final String PACKAGE_CODE = "packageCode";
    public static final String PACKAGE_CODE_SNAKE = "package_code";
    public static final String PACKAGE_VERSION = "packageVersion";
    public static final String PACKAGE_VERSION_SNAKE = "package_version";
    public static final String KNOWLEDGE_PACKAGE_ID = "knowledgePackageId";
    public static final String RELEASE_PACKAGE_ID = "releasePackageId";

    private static final List<String> GENERATED_ASSET_FIELDS = List.of(
        PACKAGE_ID,
        PACKAGE_CODE,
        PACKAGE_VERSION,
        KNOWLEDGE_PACKAGE_ID,
        RELEASE_PACKAGE_ID);

    private static final List<String> VALUE_SET_FIELDS = List.of(
        PACKAGE_ID,
        PACKAGE_CODE,
        PACKAGE_VERSION);

    private static final List<String> CODE_FIELDS = List.of(PACKAGE_CODE, PACKAGE_CODE_SNAKE);
    private static final List<String> VERSION_FIELDS = List.of(PACKAGE_VERSION, PACKAGE_VERSION_SNAKE);

    private RemovedRuntimeSelectorFields() {
    }

    public static List<String> presentIn(JsonNode content) {
        return presentIn(content, GENERATED_ASSET_FIELDS);
    }

    public static List<String> presentIn(JsonNode content, List<String> fieldNames) {
        if (content == null || !content.isObject()) {
            return List.of();
        }
        return fieldNames.stream()
            .filter(content::has)
            .sorted()
            .toList();
    }

    public static boolean hasAny(JsonNode content, List<String> fieldNames) {
        return !presentIn(content, fieldNames).isEmpty();
    }

    public static List<String> valueSetFields() {
        return VALUE_SET_FIELDS;
    }

    public static List<String> versionFields() {
        return VERSION_FIELDS;
    }

    public static String removedCode(JsonNode content) {
        return firstText(content, CODE_FIELDS);
    }

    public static String removedVersion(JsonNode content) {
        return firstText(content, VERSION_FIELDS);
    }

    private static String firstText(JsonNode content, List<String> fieldNames) {
        if (content == null || !content.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String value = text(content, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode content, String fieldName) {
        JsonNode value = content.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String raw = value.asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
