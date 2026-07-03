package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 临床路径中的阶段里程碑。
 *
 * <p>保存阶段、天序、预期完成点和达成判定条件，用于临床路径结构化查看和患者运行态达成判定。
 */
@Table("pathway_milestone")
public record PathwayMilestone(
    @Id Long id,
    @Column("milestone_id") String milestoneId,
    @Column("tenant_id") String tenantId,
    @Column("template_id") String templateId,
    @Column("phase_code") String phaseCode,
    @Column("phase_name") String phaseName,
    @Column("milestone_code") String milestoneCode,
    String name,
    @Column("day_offset") Integer dayOffset,
    @Column("expected_offset_minutes") Integer expectedOffsetMinutes,
    @Column("achievement_criteria_json") String achievementCriteriaJson,
    @Column("sort_order") Integer sortOrder,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
