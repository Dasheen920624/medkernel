package com.medkernel.engine.quality.insurance;

import java.time.Instant;

import com.medkernel.engine.evaluation.QualityFindingSeverity;

/**
 * 医保病案问题列表筛选条件。
 */
public record InsuranceIssueFilter(
    InsuranceIssueStatus status,
    QualityFindingSeverity severity,
    String departmentId,
    Instant from,
    Instant to
) {
    public InsuranceIssueFilter(
            InsuranceIssueStatus status,
            QualityFindingSeverity severity,
            String departmentId) {
        this(status, severity, departmentId, null, null);
    }
}
