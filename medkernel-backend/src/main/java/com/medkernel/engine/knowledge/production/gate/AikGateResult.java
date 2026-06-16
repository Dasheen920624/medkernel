package com.medkernel.engine.knowledge.production.gate;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 候选安全门禁结果（AIK-STD-05，FR-5 门禁可审计）。
 *
 * <p>候选提审前每过一项安全门禁记一行：候选内容指纹 + 门禁码 + 通过判定 + 不过原因 + 时点；
 * append-only 审计轨迹，不可改写（铁律 #1 不绕过、留痕可复查）。
 */
@Table("mk_aik_gate_result")
public record AikGateResult(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("content_hash") String contentHash,
    @Column("gate_code") String gateCode,
    @Column("passed") boolean passed,
    @Column("reason") String reason,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {
}
