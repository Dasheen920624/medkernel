package com.medkernel.engine.knowledge.production.initialization;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 来源版本独立批准记录，绑定精确文件摘要。 */
@Table("mk_knowledge_source_version_approval")
public record SourceVersionApproval(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("source_version_id") Long sourceVersionId,
    @Column("source_hash") String sourceHash,
    @Column("status") SourceVersionApprovalStatus status,
    @Column("approved_by") String approvedBy,
    @Column("approved_at") Instant approvedAt,
    @Column("reason") String reason,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {
}
