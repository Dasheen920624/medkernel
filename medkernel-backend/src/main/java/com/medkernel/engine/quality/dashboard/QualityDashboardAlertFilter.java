package com.medkernel.engine.quality.dashboard;

import java.time.Instant;

/**
 * 质量风险提醒列表过滤条件。
 *
 * @param from 起始时间（含），按提醒创建时间过滤
 * @param to 截止时间（不含），按提醒创建时间过滤
 * @param departmentId 责任科室过滤
 * @param status 提醒状态过滤
 * @param severity 风险级别过滤；HIGH_RISK 表示 P0/P1
 */
public record QualityDashboardAlertFilter(
    Instant from,
    Instant to,
    String departmentId,
    QualityDashboardAlertStatus status,
    String severity
) {
    QualityDashboardFilter toDashboardFilter() {
        return new QualityDashboardFilter(from, to, departmentId);
    }

    boolean hasDepartment() {
        return departmentId != null && !departmentId.isBlank();
    }

    boolean hasSeverity() {
        return severity != null && !severity.isBlank() && !"ALL".equalsIgnoreCase(severity);
    }
}
