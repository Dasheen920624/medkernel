package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则定义实体（GA-ENG-API-05 受控规则资产稳定身份）。
 *
 * <p>承担规则的租户隔离、业务编码、类型/编写模式/风险级别、状态机和当前激活版本指针；
 * {@code tenant_id + rule_code} 唯一约束保证同租户内规则编码不重复；当前 API-05 服务推进
 * {@code DRAFT → PUBLISHED}，下线、归档和回滚由后续 SYS-04 统一发布框架接管。
 */
@Table("rule_definition")
public record RuleDefinition(
    @Id Long id,
    @Column("rule_id") String ruleId,
    @Column("tenant_id") String tenantId,
    @Column("rule_code") String ruleCode,
    String name,
    @Column("rule_type") RuleType ruleType,
    @Column("authoring_mode") RuleAuthoringMode authoringMode,
    @Column("risk_level") RuleRiskLevel riskLevel,
    RuleDefinitionStatus status,
    @Column("active_version_id") String activeVersionId,
    @Column("package_version") String packageVersion,
    @Column("applicable_org_unit_id") String applicableOrgUnitId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
