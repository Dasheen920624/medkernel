package com.medkernel.engine.quality.insurance;

import java.util.List;

/**
 * 医保审核响应。
 */
public record InsuranceAuditResponse(
    String auditId,
    InsuranceAuditStatus auditStatus,
    List<InsuranceIssueResponse> issues,
    String evaluationRunId,
    int findingCount,
    int taskCount,
    String traceId
) {
    public InsuranceAuditResponse {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
