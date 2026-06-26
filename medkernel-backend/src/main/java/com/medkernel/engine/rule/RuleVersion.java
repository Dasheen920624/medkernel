package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则版本实体（GA-ENG-API-05 规则 JSON DSL 与解释模板的版本化载体）。
 *
 * <p>同租户同规则下 {@code version_no} 递增，{@code source_ref} 必填以记录指南/制度/路径/医保来源；
 * 安全复核通过并进入影子运行后，由
 * {@link RuleEngineService#transitionGovernance} 把内容状态从 {@code DRAFT} 推进到 {@code PUBLISHED}。
 */
@Table("rule_version")
public record RuleVersion(
    @Id Long id,
    @Column("version_id") String versionId,
    @Column("tenant_id") String tenantId,
    @Column("rule_id") String ruleId,
    @Column("version_no") Integer versionNo,
    @Column("source_ref") String sourceRef,
    @Column("change_summary") String changeSummary,
    @Column("dsl_json") String dslJson,
    @Column("explanation_json") String explanationJson,
    RuleVersionStatus status,
    @Column("published_at") Instant publishedAt,
    @Column("published_by") String publishedBy,
    @Column("rollback_version_id") String rollbackVersionId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
