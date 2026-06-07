package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则同行评审与临床委员会会签事实。
 */
@Table("rule_signoff")
public record RuleSignoff(
    @Id Long id,
    @Column("signoff_id") String signoffId,
    @Column("tenant_id") String tenantId,
    @Column("rule_version_id") String ruleVersionId,
    RuleSignoffStage stage,
    @Column("review_round") int reviewRound,
    @Column("signer_role") String signerRole,
    @Column("signer_id") String signerId,
    RuleSignoffDecision decision,
    String reason,
    @Column("signed_at") Instant signedAt,
    @Column("trace_id") String traceId
) {}
