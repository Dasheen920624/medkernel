package com.medkernel.engine.llm.egress;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型外调出域责任确认记录实体。
 *
 * <p>达到确认阈值的载荷由当前获授权操作者确认用途，并绑定租户、能力码与脱敏后载荷摘要。
 * 这是单人责任留痕，不是跨角色审批；未命中确认时诚实阻断（{@code ENG-LLM-007}）。
 */
@Table("mk_llm_egress_confirmation")
public record ModelEgressConfirmation(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("payload_hash") String payloadHash,
    @Column("purpose") String purpose,
    @Column("confirmed_by") String confirmedBy,
    @Column("confirmed_at") Instant confirmedAt,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
