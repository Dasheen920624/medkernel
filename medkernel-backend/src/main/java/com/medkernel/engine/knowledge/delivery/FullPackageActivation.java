package com.medkernel.engine.knowledge.delivery;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** 完整包预检确认、机构 CAS 切换和不可变运行修订之间的原子激活账本。 */
@Table("mk_knowledge_package_activation")
public record FullPackageActivation(
    @Id Long id,
    @Column("activation_id") String activationId,
    @Column("preflight_id") String preflightId,
    @Column("tenant_id") String tenantId,
    @Column("hospital_id") String hospitalId,
    @Column("authority_id") String authorityId,
    @Column("delivery_id") String deliveryId,
    @Column("preview_digest") String previewDigest,
    @Column("expected_current_release_id") String expectedCurrentReleaseId,
    @Column("runtime_release_id") String runtimeReleaseId,
    @Column("runtime_revision_no") long runtimeRevisionNo,
    @Column("baseline_release_id") String baselineReleaseId,
    @Column("activated_at") Instant activatedAt,
    @Column("activated_by") String activatedBy,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
