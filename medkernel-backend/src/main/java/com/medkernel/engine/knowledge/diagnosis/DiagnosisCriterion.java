package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 诊断标准：支持 / 反对 / 必需 / 排除某诊断的发现项（引用标准术语编码）及权重。
 *
 * <p>{@code findingTermCode} 为发现项标准术语编码（TERM-01），不写死中文；
 * {@code valueConstraint} / {@code temporalConstraint} 落库但 Spec 1 暂不求值（命中到编码级，求值挂点留后续接 RuleDslEvaluator）。
 */
@Table("mk_diagnosis_criterion")
public record DiagnosisCriterion(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("diagnosis_version_id") Long diagnosisVersionId,
    @Column("finding_term_code") String findingTermCode,
    @Column("direction") DiagnosisDirection direction,
    @Column("weight") DiagnosisWeight weight,
    @Column("value_constraint") String valueConstraint,
    @Column("temporal_constraint") String temporalConstraint,
    @Column("citation_id") Long citationId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
