package com.medkernel.engine.context;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 规则/路径维护入口的字段路径守门策略。
 *
 * <p>平台 canonical 字段必须来自 {@link ContextFieldCatalog}；院内扩展字段统一落在
 * {@code extensions.local.*}，避免维护页能保存但真实上下文无落点。
 */
public final class ContextFieldPathPolicy {

    private static final Pattern EXTENSION_FIELD_PATH =
        Pattern.compile("^extensions\\.local\\.[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*$");
    private static final Set<String> CANONICAL_FIELDS = canonicalFields();

    private ContextFieldPathPolicy() {
    }

    public static List<String> unknownFields(Collection<String> fields) {
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        if (fields == null) {
            return List.of();
        }
        for (String raw : fields) {
            String field = normalize(raw);
            if (field != null && !isKnown(field)) {
                unknown.add(field);
            }
        }
        return List.copyOf(unknown);
    }

    public static List<String> ruleDslFields(JsonNode dsl) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        collectDslFields(dsl, fields);
        return List.copyOf(fields);
    }

    public static boolean isKnown(String fieldPath) {
        String field = normalize(fieldPath);
        return field != null && (CANONICAL_FIELDS.contains(field) || EXTENSION_FIELD_PATH.matcher(field).matches());
    }

    private static Set<String> canonicalFields() {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        new ContextFieldCatalog().query(null, null)
            .forEach(field -> fields.add(field.fieldPath()));
        return Set.copyOf(fields);
    }

    private static void collectDslFields(JsonNode node, LinkedHashSet<String> fields) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectDslFields(item, fields));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (("field".equals(key) || "fact".equals(key))
                    && value != null && value.isTextual()) {
                String field = normalize(value.asText());
                if (field != null) {
                    fields.add(field);
                }
            }
            collectDslFields(value, fields);
        });
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
