package com.medkernel.engine.versioning;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 版本激活事务记录。
 */
@Table("mk_version_activation_transaction")
public record VersionActivationTransaction(
    @Id Long id,
    @Column("transaction_id") String transactionId,
    @Column("tenant_id") String tenantId,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("from_version_id") String fromVersionId,
    @Column("to_version_id") String toVersionId,
    VersionActivationAction action,
    @Column("active_scope_key") String activeScopeKey,
    @Column("impact_digest") String impactDigest,
    @Column("evidence_summary") String evidenceSummary,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
