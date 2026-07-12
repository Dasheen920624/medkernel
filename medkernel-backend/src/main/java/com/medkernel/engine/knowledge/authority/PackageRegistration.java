package com.medkernel.engine.knowledge.authority;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 平台权威不可变包注册事实。
 *
 * <p>发布序号、manifest 摘要、签发实例和密钥共同形成离线防重放与发布血缘依据。
 */
@Table("mk_knowledge_package_registration")
public record PackageRegistration(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("authority_id") String authorityId,
    @Column("delivery_id") String deliveryId,
    @Column("release_sequence") long releaseSequence,
    @Column("manifest_digest") String manifestDigest,
    @Column("platform_release_identity") String platformReleaseIdentity,
    @Column("package_file_digest") String packageFileDigest,
    @Column("package_file_size") long packageFileSize,
    @Column("storage_coordinate") String storageCoordinate,
    @Column("issuer_instance_id") String issuerInstanceId,
    @Column("key_id") String keyId,
    @Column("parent_delivery_id") String parentDeliveryId,
    @Column("parent_manifest_digest") String parentManifestDigest,
    @Column("base_manifest_digest") String baseManifestDigest,
    @Column("package_type") MedicalPackageType packageType,
    @Column("signing_status") PackageSigningStatus signingStatus,
    @Column("signed_at") Instant signedAt,
    @Column("registered_at") Instant registeredAt,
    @Version @Column("lock_version") Long lockVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
