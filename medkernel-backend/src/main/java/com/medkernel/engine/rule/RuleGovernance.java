package com.medkernel.engine.rule;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 规则版本知识治理事实。
 */
@Table("rule_governance")
public record RuleGovernance(
    @Id Long id,
    @Column("governance_id") String governanceId,
    @Column("tenant_id") String tenantId,
    @Column("rule_version_id") String ruleVersionId,
    RuleGovernanceState state,
    @Column("author_id") String authorId,
    @Column("last_reason") String lastReason,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId,
    @Version @Column("lock_version") Long lockVersion
) {
    RuleGovernance transition(
            RuleGovernanceState target,
            String reason,
            Instant now,
            String actor,
            String currentTraceId) {
        return new RuleGovernance(
            id,
            governanceId,
            tenantId,
            ruleVersionId,
            target,
            authorId,
            reason,
            createdAt,
            createdBy,
            now,
            actor,
            currentTraceId,
            lockVersion
        );
    }
}
