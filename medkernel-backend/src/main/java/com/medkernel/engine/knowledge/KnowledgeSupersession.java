package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 知识版本转换历史。每次激活 / 替换 / 撤回 / 还原都追加一条，永不修改。
 *
 * <p>用于详细规范 §1797-1806 的 lineage（来源 → 候选 → 审核 → 发布 → 替代 → 撤回链）；
 * 历史重放（{@code GET /api/v1/engine/knowledge/identities/{id}/lineage}）按 {@code transitioned_at} 排序展示。
 */
@Table("knowledge_supersession")
public record KnowledgeSupersession(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("identity_id") Long identityId,
    @Column("old_version_id") Long oldVersionId,
    @Column("new_version_id") Long newVersionId,
    @Column("transition_type") SupersessionType transitionType,
    @Column("transition_reason") String transitionReason,
    @Column("transitioned_at") Instant transitionedAt,
    @Column("transitioned_by") String transitionedBy,
    @Column("successor_identity_id") Long successorIdentityId,
    @Column("grace_period_end") Instant gracePeriodEnd,
    @Column("migration_guidance") String migrationGuidance
) {
}
