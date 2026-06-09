package com.medkernel.engine.versioning;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 灰度批次关键指标观测事实。
 */
@Table("mk_version_rollout_observation")
public record VersionRolloutObservation(
    @Id Long id,
    @Column("observation_id") String observationId,
    @Column("plan_id") String planId,
    @Column("tenant_id") String tenantId,
    @Column("stage_index") Integer stageIndex,
    @Column("sample_count") Long sampleCount,
    @Column("hit_count") Long hitCount,
    @Column("block_count") Long blockCount,
    @Column("manual_rejection_count") Long manualRejectionCount,
    @Column("anomaly_count") Long anomalyCount,
    @Column("hit_rate") BigDecimal hitRate,
    @Column("block_rate") BigDecimal blockRate,
    @Column("manual_rejection_rate") BigDecimal manualRejectionRate,
    @Column("anomaly_rate") BigDecimal anomalyRate,
    @Column("observed_at") Instant observedAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
}
