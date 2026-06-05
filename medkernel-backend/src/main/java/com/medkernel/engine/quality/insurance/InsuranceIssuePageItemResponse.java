package com.medkernel.engine.quality.insurance;

import java.math.BigDecimal;
import java.time.Instant;

import com.medkernel.engine.evaluation.QualityFindingSeverity;

/**
 * 医保病案问题列表项。
 */
public record InsuranceIssuePageItemResponse(
    String issueId,
    String claimId,
    InsuranceIssueType issueType,
    QualityFindingSeverity severity,
    InsuranceIssueStatus status,
    String ruleCode,
    String ruleVersion,
    BigDecimal claimAmount,
    BigDecimal thresholdAmount,
    String evidenceSummary,
    String departmentId,
    String evaluationRunId,
    String traceId,
    Instant createdAt
) {}
