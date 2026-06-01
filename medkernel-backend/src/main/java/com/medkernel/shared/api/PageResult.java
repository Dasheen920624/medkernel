package com.medkernel.shared.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API-13 大规模列表响应契约。
 *
 * @param items          当前页数据
 * @param nextCursor     下一页游标；为空表示没有后续页
 * @param totalEstimate  估算总数；禁止为此执行无界全表 count
 * @param totalEstimated 是否为估算值
 * @param hasMore        是否还有后续数据
 * @param <T>            数据行类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResult<T>(
    List<T> items,
    String nextCursor,
    Long totalEstimate,
    Boolean totalEstimated,
    Boolean hasMore
) {
    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
        totalEstimate = totalEstimate == null ? 0L : totalEstimate;
        totalEstimated = totalEstimated == null || totalEstimated;
        hasMore = hasMore != null && hasMore;
    }
}
