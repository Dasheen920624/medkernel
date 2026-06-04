package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 鉴别清单：与本诊断需鉴别的疾病、鉴别要点与建议补充检查。
 *
 * <p>{@code differentialIdentityId} 指向被鉴别诊断的知识身份；{@code bidirectional} 表示是否双向鉴别。
 */
@Table("mk_diagnosis_differential")
public record DiagnosisDifferential(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("diagnosis_version_id") Long diagnosisVersionId,
    @Column("differential_identity_id") Long differentialIdentityId,
    @Column("key_point") String keyPoint,
    @Column("suggested_workup") String suggestedWorkup,
    @Column("bidirectional") boolean bidirectional,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
