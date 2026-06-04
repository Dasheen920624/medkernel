package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 诊断测试病例：发现集 → 期望候选 / 置信，作为发布门禁回归集。
 *
 * <p>{@code findings} 为逗号分隔的标准发现编码；{@code expectedConfidence} 为期望分级。
 * 发布前由命中核心复算，与期望不符则阻断发布（ENG_DX_006）。
 */
@Table("mk_diagnosis_test_case")
public record DiagnosisTestCase(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("diagnosis_version_id") Long diagnosisVersionId,
    @Column("case_code") String caseCode,
    @Column("findings") String findings,
    @Column("expected_identity_id") Long expectedIdentityId,
    @Column("expected_confidence") DiagnosisConfidence expectedConfidence,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
