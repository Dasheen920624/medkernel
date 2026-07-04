package com.medkernel.engine.quality.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 质量风险概览预警实体。
 *
 * <p>保存阈值越界预警的来源事实、当前状态、阈值证据、作用域、审计字段与 trace。
 */
@Table("mk_quality_dashboard_alert")
public record QualityDashboardAlert(
    @Id Long id,
    @Column("alert_id") String alertId,
    @Column("tenant_id") String tenantId,
    @Column("department_id") String departmentId,
    @Column("alert_type") QualityDashboardAlertType alertType,
    @Column("source_type") String sourceType,
    @Column("source_id") String sourceId,
    String severity,
    QualityDashboardAlertStatus status,
    @Column("threshold_code") String thresholdCode,
    @Column("threshold_value") BigDecimal thresholdValue,
    @Column("actual_value") BigDecimal actualValue,
    String title,
    @Column("evidence_summary") String evidenceSummary,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
