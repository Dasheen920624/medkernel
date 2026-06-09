package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 专病指标绑定。
 *
 * <p>保存路径知识包、路径模板、节点和质控指标之间的关联，以及是否为必填指标。
 */
@Table("specialty_metric_binding")
public record SpecialtyMetricBinding(
    @Id Long id,
    @Column("binding_id") String bindingId,
    @Column("tenant_id") String tenantId,
    @Column("package_id") String packageId,
    @Column("template_id") String templateId,
    @Column("node_code") String nodeCode,
    @Column("metric_code") String metricCode,
    @Column("required_flag") Boolean requiredFlag,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
