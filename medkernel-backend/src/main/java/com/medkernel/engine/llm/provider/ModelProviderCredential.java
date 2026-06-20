package com.medkernel.engine.llm.provider;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 模型 Provider 租户凭据密文。
 *
 * <p>实体只保存模型凭据专用 SM4 密文、不可逆 SHA-256 指纹、尾标和轮换审计，不保存明文 Key。
 */
@Table("mk_llm_provider_credential")
public record ModelProviderCredential(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("provider_code") String providerCode,
    @Column("credential_ciphertext") String credentialCiphertext,
    @Column("credential_fingerprint") String credentialFingerprint,
    @Column("credential_last4") String credentialLast4,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId,
    @Version @Column("lock_version") Long version
) {
    @Override
    public String toString() {
        return "ModelProviderCredential[id=" + id
            + ", tenantId=" + tenantId
            + ", providerCode=" + providerCode
            + ", last4=" + credentialLast4
            + ", version=" + version + "]";
    }
}
