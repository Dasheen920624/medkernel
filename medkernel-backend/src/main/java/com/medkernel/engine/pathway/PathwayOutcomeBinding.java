package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 路径结局指标绑定。
 *
 * <p>将模板、阶段或里程碑与评估指标编码关联，形成路径疗效与质控评价闭环。
 */
@Table("pathway_outcome_binding")
public record PathwayOutcomeBinding(
    @Id Long id,
    @Column("binding_id") String bindingId,
    @Column("tenant_id") String tenantId,
    @Column("template_id") String templateId,
    PathwayOutcomeScope scope,
    @Column("ref_code") String refCode,
    @Column("indicator_code") String indicatorCode,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
