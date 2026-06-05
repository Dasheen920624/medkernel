package com.medkernel.engine.quality.value;

import java.time.Instant;

/**
 * 价值指标查询过滤条件。
 *
 * @param from 起始时间（含），按各源事实发生时间过滤
 * @param to 截止时间（不含），按各源事实发生时间过滤
 * @param departmentId 责任科室过滤；无科室维度的数据源会诚实返回不可用
 * @param hospitalId 医院过滤；当前事实源无医院维度时返回不可用
 * @param campusId 院区过滤；当前事实源无院区维度时返回不可用
 */
public record ValueMetricFilter(
    Instant from,
    Instant to,
    String departmentId,
    String hospitalId,
    String campusId
) {

    public ValueMetricFilter(Instant from, Instant to, String departmentId) {
        this(from, to, departmentId, null, null);
    }

    boolean hasDepartment() {
        return notBlank(departmentId);
    }

    boolean hasUnsupportedOrgScope() {
        return notBlank(hospitalId) || notBlank(campusId);
    }

    String unsupportedOrgScopeLabel() {
        if (notBlank(hospitalId) && notBlank(campusId)) {
            return "医院/院区";
        }
        if (notBlank(hospitalId)) {
            return "医院";
        }
        return "院区";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
