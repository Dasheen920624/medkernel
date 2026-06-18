package com.medkernel.engine.datasvc;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 引擎数据服务层 D3/D4 字段级加密账本。
 *
 * <p>表内只保存 SM4 密文、不可逆检索 hash 与审计锚点，不保存患者字段明文。
 */
@Table("mk_engine_data_encrypted_field")
public record EngineDataEncryptedField(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("scope_key") String scopeKey,
    @Column("field_name") String fieldName,
    @Column("data_level") EngineDataLevel dataLevel,
    @Column("cipher_text") String cipherText,
    @Column("cipher_algorithm") String cipherAlgorithm,
    @Column("key_ref") String keyRef,
    @Column("search_hash") String searchHash,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("trace_id") String traceId
) {
}
