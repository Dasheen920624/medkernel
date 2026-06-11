package com.medkernel.engine.list;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.medkernel.shared.api.PageQuery;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 大规模列表资源定义：集中声明排序与过滤白名单。
 */
record LargeListResourceDefinition(
    Set<String> sortFields,
    Set<String> filterFields
) {
    private static final LargeListResourceDefinition AUDIT_EVENTS = new LargeListResourceDefinition(
        Set.of("id"),
        Set.of(
            "action",
            "resourceType",
            "actorUserId",
            "traceId",
            "outcome",
            "environmentKey",
            "orgPathPrefix",
            "from",
            "to",
            "superAdminOnly"
        )
    );

    private static final LargeListResourceDefinition TERMINOLOGY_MAPPINGS = new LargeListResourceDefinition(
        Set.of("id"),
        Set.of("sourceSystem", "category", "status", "keyword")
    );

    static LargeListResourceDefinition auditEvents() {
        return AUDIT_EVENTS;
    }

    static LargeListResourceDefinition terminologyMappings() {
        return TERMINOLOGY_MAPPINGS;
    }

    SortSpec validateSort(String sort) {
        String expression = sort == null || sort.isBlank() ? PageQuery.DEFAULT_SORT : sort.trim();
        String[] parts = expression.split(",");
        if (parts.length > 2) {
            throw new ApiException(ErrorCode.SORT_FIELD_NOT_ALLOWED, "排序表达式只能包含字段和方向: " + sort);
        }
        String field = parts[0].trim();
        String normalizedField = field.toLowerCase(Locale.ROOT);
        if (!sortFields.contains(normalizedField)) {
            throw new ApiException(ErrorCode.SORT_FIELD_NOT_ALLOWED, "排序字段不允许: " + field);
        }
        String direction = parts.length == 2 ? parts[1].trim().toUpperCase(Locale.ROOT) : "DESC";
        if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
            throw new ApiException(ErrorCode.SORT_FIELD_NOT_ALLOWED, "排序方向只允许 ASC 或 DESC: " + direction);
        }
        return new SortSpec(normalizedField, direction);
    }

    Map<String, String> validateFilters(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }
        Map<String, String> validated = new LinkedHashMap<>();
        filters.forEach((key, value) -> {
            if (!filterFields.contains(key)) {
                throw new ApiException(ErrorCode.FILTER_FIELD_NOT_ALLOWED, "过滤字段不允许: " + key);
            }
            validated.put(key, value);
        });
        return validated;
    }

    record SortSpec(String field, String direction) {
    }
}
