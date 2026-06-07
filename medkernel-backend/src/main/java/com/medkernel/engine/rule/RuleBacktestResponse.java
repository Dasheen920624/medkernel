package com.medkernel.engine.rule;

import java.time.Instant;
import java.util.List;

/**
 * 规则历史回测结果，灵敏度按阳性金标准计算，特异度按阴性金标准计算。
 */
public record RuleBacktestResponse(
    String backtestId,
    String ruleId,
    String versionId,
    String cohortRef,
    int sampleCount,
    int truePositiveCount,
    int falsePositiveCount,
    int trueNegativeCount,
    int falseNegativeCount,
    double sensitivity,
    double specificity,
    double accuracy,
    double fireRate,
    List<String> falsePositiveCaseIds,
    List<String> falseNegativeCaseIds,
    Instant createdAt,
    String traceId
) {}
