package com.medkernel.engine.knowledge.discovery;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 最新知识探索差异台账。
 *
 * <p>只记录新增、修订、废止差异和来源依据，不自动写入权威版本或替换现行知识。
 */
@Table("mk_knowledge_diff")
public record KnowledgeDiff(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("run_code") String runCode,
    @Column("target_identity_id") Long targetIdentityId,
    @Column("current_version_id") Long currentVersionId,
    @Column("asset_identity") String assetIdentity,
    @Column("current_content_hash") String currentContentHash,
    @Column("candidate_content_hash") String candidateContentHash,
    @Column("diff_type") KnowledgeDiffType diffType,
    @Column("basis") String basis,
    @Column("source_ref") String sourceRef,
    @Column("detected_at") Instant detectedAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
}
