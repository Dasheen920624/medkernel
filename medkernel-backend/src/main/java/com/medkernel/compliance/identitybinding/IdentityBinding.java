package com.medkernel.compliance.identitybinding;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 外部身份绑定持久化实体。
 *
 * <p>外部身份原文不落库，只保存 SM3 摘要和脱敏提示；解绑保留当前状态，完整变更历史进入统一审计链。
 */
@Table("mk_compliance_identity_binding")
public record IdentityBinding(
    @Id Long id,
    @Column("binding_id") String bindingId,
    @Column("tenant_id") String tenantId,
    @Column("user_id") String userId,
    @Column("provider_type") String providerType,
    @Column("external_subject_digest") String externalSubjectDigest,
    @Column("subject_hint") String subjectHint,
    @Column("status") String status,
    @Column("version") Long version,
    @Column("unbound_reason") String unboundReason,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
}
