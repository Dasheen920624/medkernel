package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 诊疗指针：确诊后指向治疗 / 检查（规则·知识）或专病路径（恒软建议）。
 *
 * <p>{@code isSoft} 恒为软建议（不自动执行、不自动下医嘱）；目标由类型和编码共同确定。
 */
@Table("mk_diagnosis_care_pointer")
public record DiagnosisCarePointer(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("diagnosis_version_id") Long diagnosisVersionId,
    @Column("pointer_type") DiagnosisCarePointerType pointerType,
    @Column("target_type") DiagnosisCareTargetType targetType,
    @Column("target_ref") String targetRef,
    @Column("is_soft") boolean isSoft,
    @Column("description") String description,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
