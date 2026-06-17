package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 权威知识版本失效记录。
 *
 * <p>失效记录区别于普通状态字段：它保留替换或安全原因、授权人、适用域和加急审核标识，
 * 支撑 SYS-08 的旧版退出新临床决策与后续审计导出。
 */
@Table("mk_knowledge_invalidation")
public record KnowledgeInvalidation(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("identity_id") Long identityId,
    @Column("version_id") Long versionId,
    @Column("invalidation_type") KnowledgeInvalidationType invalidationType,
    @Column("status") KnowledgeInvalidationStatus status,
    @Column("risk_level") KnowledgeRiskLevel riskLevel,
    @Column("reason") String reason,
    @Column("organization_scope") String organizationScope,
    @Column("applicable_scope") String applicableScope,
    @Column("authorized_by") String authorizedBy,
    @Column("invalidated_at") Instant invalidatedAt,
    @Column("expedited_review_required") Boolean expeditedReviewRequired,
    @Column("trace_id") String traceId,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
