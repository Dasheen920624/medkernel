package com.medkernel.engine.llm.egress;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型外调出域证据实体（LLM-03 FR-5）。
 *
 * <p>每次真实出域留痕：出域字段清单、脱敏后内容 SHA-256、审批引用、目标 provider，
 * 供合规审计追溯数据出境（[SYS-06]/[EVID-01]）。
 */
@Table("model_egress_evidence")
public record ModelEgressEvidence(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("task_id") String taskId,
    @Column("egress_fields") String egressFields,
    @Column("desensitized_hash") String desensitizedHash,
    @Column("approval_id") Long approvalId,
    @Column("provider_code") String providerCode,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
