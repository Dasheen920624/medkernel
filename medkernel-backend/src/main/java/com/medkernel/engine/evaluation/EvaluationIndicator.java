package com.medkernel.engine.evaluation;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 评估指标版本实体。
 *
 * <p>一个记录表示同租户、同指标编码下的一个不可变口径版本；状态按
 * {@code DRAFT -> PENDING_REVIEW -> PUBLISHED -> GRAY -> ACTIVE -> OFFLINE -> ARCHIVED} 流转。
 */
@Table("evaluation_indicator")
public record EvaluationIndicator(
    @Id Long id,
    @Column("indicator_id") String indicatorId,
    @Column("tenant_id") String tenantId,
    @Column("indicator_code") String indicatorCode,
    @Column("version_no") int versionNo,
    String name,
    @Column("subject_type") EvaluationSubjectType subjectType,
    @Column("denominator_definition") String denominatorDefinition,
    @Column("numerator_definition") String numeratorDefinition,
    @Column("exclusion_definition") String exclusionDefinition,
    @Column("scoring_definition") String scoringDefinition,
    @Column("time_window") String timeWindow,
    @Column("organization_scope") String organizationScope,
    @Column("responsible_department_id") String responsibleDepartmentId,
    @Column("source_ref") String sourceRef,
    EvaluationIndicatorStatus status,
    @Column("published_at") Instant publishedAt,
    @Column("published_by") String publishedBy,
    @Column("activated_at") Instant activatedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
