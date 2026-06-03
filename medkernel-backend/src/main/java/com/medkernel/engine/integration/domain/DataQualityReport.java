package com.medkernel.engine.integration.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 数据质量报告快照。
 */
@Table("mk_integration_data_quality_report")
public record DataQualityReport(
    @Id
    @Column("report_id")
    String reportId,
    @Column("tenant_id")
    String tenantId,
    @Column("generated_at")
    Instant generatedAt,
    @Column("required_field_total")
    Integer requiredFieldTotal,
    @Column("required_field_present")
    Integer requiredFieldPresent,
    @Column("required_field_rate")
    Double requiredFieldRate,
    @Column("adapter_total")
    Integer adapterTotal,
    @Column("mapped_adapter_count")
    Integer mappedAdapterCount,
    @Column("mapping_rate")
    Double mappingRate,
    @Column("timely_adapter_count")
    Integer timelyAdapterCount,
    @Column("timeliness_rate")
    Double timelinessRate,
    @Column("not_connected_count")
    Integer notConnectedCount,
    @Column("misconfigured_count")
    Integer misconfiguredCount,
    @Column("gap_summary")
    String gapSummary,
    @Column("created_at")
    Instant createdAt,
    @Column("created_by")
    String createdBy,
    @Column("trace_id")
    String traceId
) {}
