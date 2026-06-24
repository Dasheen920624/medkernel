package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则发布门禁测试用例实体（GA-ENG-API-05）。
 *
 * <p>记录用例输入快照、期望命中/严重度/命中后处理以及最近一次试运行结果；
 * 发布要求阳性、阴性、边界、冲突四类用例齐备且全部 PASS，详见
 * {@link RuleEngineService#transitionGovernance}。
 */
@Table("rule_test_case")
public record RuleTestCase(
    @Id Long id,
    @Column("case_id") String caseId,
    @Column("tenant_id") String tenantId,
    @Column("rule_id") String ruleId,
    @Column("version_id") String versionId,
    @Column("case_type") RuleTestCaseType caseType,
    @Column("context_snapshot_id") String contextSnapshotId,
    @Column("input_payload") String inputPayload,
    @Column("expected_hit") Boolean expectedHit,
    @Column("expected_severity") RuleRiskLevel expectedSeverity,
    @Column("expected_action_code") String expectedActionCode,
    @Column("last_hit") Boolean lastHit,
    @Column("last_status") RuleTestCaseStatus lastStatus,
    @Column("last_message") String lastMessage,
    @Column("last_run_at") Instant lastRunAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
