package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;

/**
 * 知识候选生产血缘（AIK-STD-13 PR2，FR-5 每候选可回溯）。
 *
 * <p>每条提交候选记一行回溯元数据：归属 job（→生产器/管道/模型策略）+ 资产身份 + 真实内容指纹 + 候选引用 + 时点。
 * <b>非资产存储</b>——不存候选正文 / sources 内容（候选物化走既有版本/审核链），本表仅留生产血缘轨迹。
 */
@Table("mk_knowledge_production_candidate")
public record KnowledgeProductionCandidate(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("asset_identity") String assetIdentity,
    @Column("content_hash") String contentHash,
    @Column("candidate_ref") String candidateRef,
    @Column("risk_level") KnowledgeRiskLevel riskLevel,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("explain_json") String explainJson
) {
    public KnowledgeProductionCandidate(Long id, String tenantId, String jobCode, String assetIdentity,
                                        String contentHash, String candidateRef, KnowledgeRiskLevel riskLevel,
                                        Instant createdAt, String createdBy) {
        this(id, tenantId, jobCode, assetIdentity, contentHash, candidateRef, riskLevel, createdAt, createdBy, null);
    }
}
