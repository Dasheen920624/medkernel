package com.medkernel.engine.rule;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则版本适用域的结构化检索镜像，权威内容仍为 {@code rule_version.dsl_json.applicability}。
 */
@Table("rule_applicability")
public record RuleApplicability(
    @Id Long id,
    @Column("rule_version_id") String ruleVersionId,
    @Column("tenant_id") String tenantId,
    @Column("population_json") String populationJson,
    @Column("org_scope_json") String orgScopeJson,
    @Column("settings_json") String settingsJson,
    @Column("effective_from") LocalDate effectiveFrom,
    @Column("effective_to") LocalDate effectiveTo,
    @Column("rollout_percent") Integer rolloutPercent,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
