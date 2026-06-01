package com.medkernel.shared.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * API-13 大规模列表查询契约。
 *
 * <p>普通页码分页继续使用 {@link PageRequest}；10 万级深翻页必须使用本契约的游标字段。
 *
 * @param cursor  上一页返回的游标；首次请求为空
 * @param size    每页条数，默认 50，最大 500；超过上限直接拒绝，禁止静默截断
 * @param offset  浅分页偏移量；深翻页应使用 cursor
 * @param sort    排序表达式，如 id,desc；字段必须由资源白名单校验
 * @param filters 过滤字段；字段必须由资源白名单校验
 */
public record PageQuery(
    String cursor,
    @Min(1) @Max(500) Integer size,
    Long offset,
    String sort,
    Map<String, String> filters
) {
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 500;
    public static final String DEFAULT_SORT = "id,desc";

    public PageQuery {
        cursor = blankToNull(cursor);
        sort = blankToDefault(sort);
        filters = filters == null ? Map.of() : Map.copyOf(cleanFilters(filters));
    }

    public static PageQuery first() {
        return new PageQuery(null, DEFAULT_SIZE, 0L, DEFAULT_SORT, Map.of());
    }

    public int validatedSize() {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            throw new ApiException(
                ErrorCode.PAGE_SIZE_EXCEEDED,
                "请求页大小 " + size + " 超过大规模列表上限 " + MAX_SIZE);
        }
        return size;
    }

    public long safeOffset() {
        return offset == null || offset < 0 ? 0L : offset;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value) {
        return value == null || value.isBlank() ? DEFAULT_SORT : value.trim();
    }

    private static Map<String, String> cleanFilters(Map<String, String> source) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                return;
            }
            cleaned.put(key.trim(), value.trim());
        });
        return cleaned;
    }
}
