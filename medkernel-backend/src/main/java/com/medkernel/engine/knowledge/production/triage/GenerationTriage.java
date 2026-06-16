package com.medkernel.engine.knowledge.production.triage;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * AIK-STD-10 生成期候选身份识别、去重与 8 态分流结果。
 */
@Table("mk_knowledge_generation_triage")
public record GenerationTriage(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("content_hash") String contentHash,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("target_identity_id") Long targetIdentityId,
    @Column("active_version_id") Long activeVersionId,
    @Column("matched_version_id") Long matchedVersionId,
    @Column("triage_state") GenerationTriageState triageState,
    @Column("action") GenerationTriageAction action,
    @Column("basis") String basis,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {
}
