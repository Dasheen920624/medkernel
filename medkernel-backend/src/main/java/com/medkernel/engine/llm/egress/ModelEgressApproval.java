package com.medkernel.engine.llm.egress;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型外调出域审批记录实体（LLM-03 FR-3）。
 *
 * <p>高敏出域需先经合规/安全人工审批；按租户+能力码+脱敏后载荷 hash 检索最近一条 {@code APPROVED} 记录，
 * 命中方可放行，否则诚实阻断（{@code ENG-LLM-007}），不静默出域。
 */
@Table("model_egress_approval")
public record ModelEgressApproval(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("payload_hash") String payloadHash,
    @Column("status") String status, // PENDING, APPROVED, REJECTED
    @Column("approver") String approver,
    @Column("decided_at") Instant decidedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
