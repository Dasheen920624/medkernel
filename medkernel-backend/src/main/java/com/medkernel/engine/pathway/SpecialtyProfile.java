package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 专病画像。
 *
 * <p>保存路径知识包下的分型、风险分层、准入条件、退出条件和随访计划摘要。
 */
@Table("specialty_profile")
public record SpecialtyProfile(
    @Id Long id,
    @Column("profile_id") String profileId,
    @Column("tenant_id") String tenantId,
    @Column("package_id") String packageId,
    @Column("profile_code") String profileCode,
    String name,
    @Column("stratification_json") String stratificationJson,
    @Column("entry_criteria_json") String entryCriteriaJson,
    @Column("exit_criteria_json") String exitCriteriaJson,
    @Column("followup_plan_json") String followupPlanJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
