package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 患者路径运行实例。
 *
 * <p>保存患者、就诊、入径时机构生效版本、精确路径版本、临床路径、当前节点、运行状态、
 * 入径时间、完成/退出时间和最后事件 ID。机构生效版本和路径版本在实例生命周期内不可改变。
 */
@Table("patient_pathway")
public record PatientPathway(
    @Id Long id,
    @Column("patient_pathway_id") String patientPathwayId,
    @Column("tenant_id") String tenantId,
    @Column("patient_id") String patientId,
    @Column("encounter_id") String encounterId,
    @Column("template_id") String templateId,
    @Column("runtime_release_id") String runtimeReleaseId,
    @Column("pathway_version_id") String pathwayVersionId,
    @Column("current_node_code") String currentNodeCode,
    PatientPathwayStatus status,
    @Column("entered_at") Instant enteredAt,
    @Column("completed_at") Instant completedAt,
    @Column("exited_at") Instant exitedAt,
    @Column("exit_reason") String exitReason,
    @Column("last_event_id") String lastEventId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
