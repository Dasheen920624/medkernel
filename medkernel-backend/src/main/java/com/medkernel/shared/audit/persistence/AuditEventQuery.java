package com.medkernel.shared.audit.persistence;

import java.time.Instant;

/**
 * 审计查询过滤条件。
 *
 * <p>查询端只接收筛选条件 + 游标 + 页大小；{@code tenantId} 在 {@code AuditQueryService}
 * 内部由 {@code RequestContext.currentOrgScope()} 注入，禁止由调用方传入。
 *
 * @param action       动作过滤；null 表示不过滤
 * @param resourceType 资源类型过滤；null 表示不过滤
 * @param actorUserId  操作人过滤；null 表示不过滤
 * @param traceId      追踪号ID过滤；null 表示不过滤
 * @param orgPathPrefix 组织路径前缀过滤；null 表示不过滤
 * @param environmentKey 环境过滤；null 表示不过滤
 * @param outcome      成功/失败结果过滤；null 表示不过滤
 * @param superAdminOnly 只返回超管高亮事件
 * @param from         起始时间（含）；null 表示不限
 * @param to           结束时间（不含）；null 表示不限
 * @param cursor       上一页末行的 id；null 表示首次请求
 * @param size         请求页大小；由调用方在进入仓库前完成上限校验
 * @param offset       浅分页偏移量；深翻页应使用 cursor
 * @param sortField    允许范围内的排序字段
 * @param sortDirection 排序方向：ASC 或 DESC
 */
public record AuditEventQuery(
    String action,
    String resourceType,
    String actorUserId,
    String traceId,
    String orgPathPrefix,
    String environmentKey,
    String outcome,
    boolean superAdminOnly,
    Instant from,
    Instant to,
    Long cursor,
    int size,
    Long offset,
    String sortField,
    String sortDirection
) {

    public AuditEventQuery(String action,
                           String resourceType,
                           String actorUserId,
                           String orgPathPrefix,
                           String environmentKey,
                           String outcome,
                           boolean superAdminOnly,
                           Instant from,
                           Instant to,
                           Long cursor,
                           int size) {
        this(action, resourceType, actorUserId, null, orgPathPrefix, environmentKey, outcome,
            superAdminOnly, from, to, cursor, size, 0L, "id", "DESC");
    }

    public AuditEventQuery(String action,
                           String resourceType,
                           String actorUserId,
                           String traceId,
                           String orgPathPrefix,
                           String environmentKey,
                           String outcome,
                           boolean superAdminOnly,
                           Instant from,
                           Instant to,
                           Long cursor,
                           int size) {
        this(action, resourceType, actorUserId, traceId, orgPathPrefix, environmentKey, outcome,
            superAdminOnly, from, to, cursor, size, 0L, "id", "DESC");
    }

    public AuditEventQuery(String action,
                           String resourceType,
                           String actorUserId,
                           String orgPathPrefix,
                           String environmentKey,
                           String outcome,
                           boolean superAdminOnly,
                           Instant from,
                           Instant to,
                           Long cursor,
                           int size,
                           Long offset,
                           String sortField,
                           String sortDirection) {
        this(action, resourceType, actorUserId, null, orgPathPrefix, environmentKey, outcome,
            superAdminOnly, from, to, cursor, size, offset, sortField, sortDirection);
    }

    public AuditEventQuery(String action,
                           String resourceType,
                           String actorUserId,
                           Instant from,
                           Instant to,
                           Long cursor,
                           int size) {
        this(action, resourceType, actorUserId, null, null, null, null, false, from, to, cursor, size);
    }

    long safeOffset() {
        return offset == null || offset < 0 ? 0L : offset;
    }

    String safeSortField() {
        return sortField == null || sortField.isBlank() ? "id" : sortField.trim();
    }

    String safeSortDirection() {
        return "ASC".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";
    }
}
