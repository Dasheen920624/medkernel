package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 参数化规则实例参数值。
 *
 * <p>参数 schema 保存在 {@code rule_version.dsl_json.meta.parameters}，本表只保存某一规则版本的
 * 实例取值，便于审计、批量生成和后续影响分析。
 */
@Table("mk_engine_rule_parameter_binding")
public record RuleParameterBinding(
    @Id Long id,
    @Column("rule_version_id") String ruleVersionId,
    @Column("tenant_id") String tenantId,
    @Column("param_key") String paramKey,
    @Column("param_value_json") String paramValueJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {}
