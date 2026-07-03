package com.medkernel.engine.quality.dashboard;

import java.time.Instant;

/**
 * 质量管理概览查询过滤条件。
 *
 * @param from 起始时间（含），按来源事实发生时间过滤
 * @param to 截止时间（不含），按来源事实发生时间过滤
 * @param departmentId 责任科室过滤
 */
public record QualityDashboardFilter(
    Instant from,
    Instant to,
    String departmentId
) {
    boolean hasDepartment() {
        return departmentId != null && !departmentId.isBlank();
    }
}
