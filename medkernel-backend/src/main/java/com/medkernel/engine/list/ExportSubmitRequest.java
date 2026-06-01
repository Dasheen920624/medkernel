package com.medkernel.engine.list;

import java.util.Map;

/**
 * 异步批量导出任务提交请求。
 *
 * @param resourceType   导出的列表资源类型（如 AUDIT_EVENT）
 * @param filters        多字段动态筛选条件字典
 * @param selectedScope  导出范围：CURRENT_PAGE 或 FILTERED_RESULT
 * @param idempotencyKey 幂等键，可由前端用 Idempotency-Key 透传
 */
public record ExportSubmitRequest(
    String resourceType,
    Map<String, String> filters,
    String selectedScope,
    String idempotencyKey
) {

    public ExportSubmitRequest {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        selectedScope = (selectedScope == null || selectedScope.isBlank())
            ? "FILTERED_RESULT"
            : selectedScope.trim().toUpperCase();
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    }

    public ExportSubmitRequest(String resourceType, Map<String, String> filters) {
        this(resourceType, filters, "FILTERED_RESULT", null);
    }

    public ExportSubmitRequest withIdempotencyKey(String fallbackIdempotencyKey) {
        if (idempotencyKey != null) {
            return this;
        }
        return new ExportSubmitRequest(resourceType, filters, selectedScope, fallbackIdempotencyKey);
    }
}
