package com.medkernel.engine.quality.insurance;

import java.math.BigDecimal;

import com.medkernel.engine.evaluation.QualityFindingSeverity;

/**
 * 医保病案问题响应。
 */
public record InsuranceIssueResponse(
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
    String traceId
) {}
