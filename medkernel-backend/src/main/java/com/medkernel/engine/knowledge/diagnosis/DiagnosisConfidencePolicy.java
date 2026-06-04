package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 置信分级策略：权重 → 等级阈值，可按租户 / 科室 {@code scopeKey} 覆盖，<b>不硬编码</b>。
 *
 * <p>{@code strongMinMajor} 判 STRONG 所需主要标准数；{@code requireAllRequired} 是否要求全部必需项；
 * {@code moderateMinHits} 判 MODERATE 所需命中数。运行时未覆盖回退平台主租户 t-1 的 DEFAULT。
 */
@Table("mk_diagnosis_confidence_policy")
public record DiagnosisConfidencePolicy(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("scope_key") String scopeKey,
    @Column("strong_min_major") int strongMinMajor,
    @Column("require_all_required") boolean requireAllRequired,
    @Column("moderate_min_hits") int moderateMinHits,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
