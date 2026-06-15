package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 知识生产编排 job（AIK-STD-13，FR-1/4/5）。
 *
 * <p>统一编排层的生产任务：定义来源范围 + 资产类型 + 生产器 + 目标管道 + 模型策略，记录状态/血缘/审计。
 * 候选不在本表存正文——候选物化走既有版本/审核链（经 {@link KnowledgeCandidateIntake} 端口），本表仅留编排元数据与血缘。
 */
@Table("mk_knowledge_production_job")
public record KnowledgeProductionJob(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("source_scope") String sourceScope,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("producer") KnowledgeProducer producer,
    @Column("target_pipeline") TargetPipeline targetPipeline,
    @Column("model_strategy") String modelStrategy,
    @Column("status") ProductionJobStatus status,
    @Column("candidate_count") int candidateCount,
    @Column("lineage") String lineage,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
