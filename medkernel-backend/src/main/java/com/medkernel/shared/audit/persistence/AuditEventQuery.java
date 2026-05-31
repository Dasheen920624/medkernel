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
 * @param orgPathPrefix 组织路径前缀过滤；null 表示不过滤
 * @param environmentKey 环境过滤；null 表示不过滤
 * @param outcome      成功/失败结果过滤；null 表示不过滤
 * @param superAdminOnly 只返回超管高亮事件
 * @param from         起始时间（含）；null 表示不限
 * @param to           结束时间（不含）；null 表示不限
 * @param cursor       上一页末行的 id；null 表示首次请求
 * @param size         请求页大小；由 {@link com.medkernel.shared.api.CursorRequest} 规约
 */
public record AuditEventQuery(
    String action,
    String resourceType,
    String actorUserId,
    String orgPathPrefix,
    String environmentKey,
    String outcome,
    boolean superAdminOnly,
    Instant from,
    Instant to,
    Long cursor,
    int size
) {

    public AuditEventQuery(String action,
                           String resourceType,
                           String actorUserId,
                           Instant from,
                           Instant to,
                           Long cursor,
                           int size) {
        this(action, resourceType, actorUserId, null, null, null, false, from, to, cursor, size);
    }
}
