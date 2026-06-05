package com.medkernel.engine.quality.insurance;

import java.math.BigDecimal;

import com.medkernel.engine.evaluation.QualityFindingSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 单条医保审核规则条件。
 *
 * <p>阈值、期望状态和期望结算类型全部来自请求或上游配置；服务只执行确定性比较，不内置医学口径。
 */
public record InsuranceAuditRuleRequest(
    @NotBlank String ruleCode,
    @NotBlank String ruleVersion,
    @NotNull InsuranceIssueType issueType,
    @NotNull QualityFindingSeverity severity,
    BigDecimal maxAmount,
    String requiredClaimStatus,
    String requiredClaimType,
    @NotBlank String description
) {}
