package com.medkernel.engine.knowledge.discovery;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 可信来源探索运行的检索时点存证（LLM-06，FR-2）。
 *
 * <p>每次探索记一行：记录检索时点（{@code executedAt}）+ 当时检索的源版本快照（{@code sourceSnapshot}）+
 * 结果集真实指纹（{@code resultHash}），用于复查「当时看到什么」。候选正文不落本表（流向审核链，AIK-STD-13 落库），
 * 本表仅留可复算核验的源版本快照与结果指纹，避免重复存储。
 */
@Table("mk_knowledge_discovery_run")
public record DiscoveryRun(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("run_code") String runCode,
    @Column("query_text") String queryText,
    @Column("capability_code") String capabilityCode,
    @Column("executed_at") Instant executedAt,
    @Column("source_snapshot") String sourceSnapshot,
    @Column("hit_count") int hitCount,
    @Column("candidate_count") int candidateCount,
    @Column("result_hash") String resultHash,
    @Column("status") DiscoveryRunStatus status,
    @Column("degraded") boolean degraded,
    @Column("created_by") String createdBy,
    @Column("created_at") Instant createdAt
) {
}
