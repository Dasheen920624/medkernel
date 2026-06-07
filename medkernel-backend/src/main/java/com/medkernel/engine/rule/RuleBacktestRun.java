package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则历史回测事实，基于带金标准标注的真实脱敏快照集计算。
 */
@Table("rule_backtest_run")
public record RuleBacktestRun(
    @Id Long id,
    @Column("backtest_id") String backtestId,
    @Column("tenant_id") String tenantId,
    @Column("rule_id") String ruleId,
    @Column("version_id") String versionId,
    @Column("cohort_ref") String cohortRef,
    @Column("sample_count") int sampleCount,
    @Column("true_positive_count") int truePositiveCount,
    @Column("false_positive_count") int falsePositiveCount,
    @Column("true_negative_count") int trueNegativeCount,
    @Column("false_negative_count") int falseNegativeCount,
    double sensitivity,
    double specificity,
    double accuracy,
    @Column("fire_rate") double fireRate,
    @Column("false_positive_examples_json") String falsePositiveExamplesJson,
    @Column("false_negative_examples_json") String falseNegativeExamplesJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {}
