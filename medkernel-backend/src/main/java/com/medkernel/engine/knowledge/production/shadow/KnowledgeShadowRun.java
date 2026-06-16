package com.medkernel.engine.knowledge.production.shadow;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 生成期影子评测运行记录（AIK-STD-06）。
 *
 * <p>记录候选在进入人工审核前的回归用例命中、漏报、退化与达标裁决；只留影子证据，不写临床动作。
 */
@Table("mk_knowledge_shadow_run")
public record KnowledgeShadowRun(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("target_identity_id") Long targetIdentityId,
    @Column("content_hash") String contentHash,
    @Column("capability_code") String capabilityCode,
    @Column("status") KnowledgeShadowRunStatus status,
    @Column("total_cases") int totalCases,
    @Column("hit_count") int hitCount,
    @Column("false_positive_count") int falsePositiveCount,
    @Column("miss_count") int missCount,
    @Column("degradation_detected") boolean degradationDetected,
    @Column("ready_for_review") boolean readyForReview,
    @Column("basis") String basis,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {
}
