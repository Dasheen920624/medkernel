package com.medkernel.engine.knowledge.delivery;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 已通过固定根验签和完整只读校验的医院包预检不可变事实。 */
@Table("mk_knowledge_package_preflight")
public record FullPackagePreflight(
    @Id Long id,
    @Column("preflight_id") String preflightId,
    @Column("tenant_id") String tenantId,
    @Column("hospital_id") String hospitalId,
    @Column("authority_id") String authorityId,
    @Column("delivery_id") String deliveryId,
    @Column("release_sequence") long releaseSequence,
    @Column("manifest_digest") String manifestDigest,
    @Column("platform_release_identity") String platformReleaseIdentity,
    @Column("package_file_digest") String packageFileDigest,
    @Column("package_file_size") long packageFileSize,
    @Column("quarantine_coordinate") String quarantineCoordinate,
    @Column("issuer_instance_id") String issuerInstanceId,
    @Column("key_id") String keyId,
    @Column("root_fingerprint") String rootFingerprint,
    @Column("status") FullPackagePreflightStatus status,
    @Column("preview_digest") String previewDigest,
    @Column("preview_json") String previewJson,
    @Version @Column("lock_version") Long lockVersion,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
