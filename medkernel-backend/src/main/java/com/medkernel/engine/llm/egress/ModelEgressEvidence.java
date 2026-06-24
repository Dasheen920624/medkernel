package com.medkernel.engine.llm.egress;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型外调证据实体（LLM-03 FR-5）。
 *
 * <p>每次真实外调留痕：外调字段清单、脱敏后内容 SHA-256、责任确认引用、目标模型服务，
 * 供合规审计追溯数据出境（[SYS-06]/[EVID-01]）。
 */
@Table("mk_llm_egress_evidence")
public record ModelEgressEvidence(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("capability_code") String capabilityCode,
    @Column("task_id") String taskId,
    @Column("egress_fields") String egressFields,
    @Column("desensitized_hash") String desensitizedHash,
    @Column("confirmation_id") Long confirmationId,
    @Column("provider_code") String providerCode,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {}
