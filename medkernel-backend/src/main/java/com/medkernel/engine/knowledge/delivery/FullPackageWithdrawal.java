package com.medkernel.engine.knowledge.delivery;

import java.time.Instant;

import com.medkernel.engine.versioning.VersionedAssetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 完整包携带的安全撤回不可变事实；被撤回版本不存在于空库时仍须保留。 */
@Table("mk_knowledge_package_withdrawal")
public record FullPackageWithdrawal(
    @Id Long id,
    @Column("withdrawal_id") String withdrawalId,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("delivery_id") String deliveryId,
    @Column("release_sequence") long releaseSequence,
    @Column("asset_type") VersionedAssetType assetType,
    @Column("asset_identity") String assetIdentity,
    @Column("withdrawn_version_id") String withdrawnVersionId,
    @Column("successor_version_id") String successorVersionId,
    @Column("reason_digest") String reasonDigest,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
