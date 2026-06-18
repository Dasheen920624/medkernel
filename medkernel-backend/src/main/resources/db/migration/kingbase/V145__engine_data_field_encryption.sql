-- MedKernel 第二阶段 P6-T6.4 · DATASVC-01 D3/D4 字段级加密与分级元数据（KingbaseES）
-- 新项目基线：建立独立加密账本与字段分级元数据，不迁移旧明文字段。

CREATE TABLE IF NOT EXISTS mk_engine_data_encrypted_field (
    id               BIGSERIAL     PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    scope_key        VARCHAR(128)  NOT NULL,
    field_name       VARCHAR(128)  NOT NULL,
    data_level       VARCHAR(8)    NOT NULL,
    cipher_text      VARCHAR(2048) NOT NULL,
    cipher_algorithm VARCHAR(64)   NOT NULL,
    key_ref          VARCHAR(128)  NOT NULL,
    search_hash      VARCHAR(96)   NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)   NULL,
    trace_id         VARCHAR(128)  NULL,
    CONSTRAINT ck_mk_engine_data_encrypted_field_level CHECK (data_level IN ('D3','D4')),
    CONSTRAINT ck_mk_engine_data_encrypted_field_cipher CHECK (cipher_algorithm IN ('SM4/ECB/PKCS5Padding')),
    CONSTRAINT ck_mk_engine_data_encrypted_field_hash CHECK (search_hash LIKE 'sm3:%')
);

CREATE TABLE IF NOT EXISTS mk_engine_data_field_policy (
    id                       BIGSERIAL    PRIMARY KEY,
    tenant_id                VARCHAR(64)  NOT NULL,
    field_path               VARCHAR(256) NOT NULL,
    data_level               VARCHAR(8)   NOT NULL,
    encryption_required_flag CHAR(1)      NOT NULL DEFAULT 'N',
    allowed_channel          VARCHAR(48)  NOT NULL,
    status                   VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               VARCHAR(64)  NULL,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(64)  NULL,
    trace_id                 VARCHAR(128) NULL,
    CONSTRAINT uk_mk_engine_data_field_policy_path UNIQUE (tenant_id, field_path),
    CONSTRAINT ck_mk_engine_data_field_policy_level CHECK (data_level IN ('D0','D1','D2','D3','D4','D5')),
    CONSTRAINT ck_mk_engine_data_field_policy_encrypt CHECK (
        encryption_required_flag IN ('Y','N')
        AND (data_level NOT IN ('D3','D4') OR encryption_required_flag = 'Y')
    ),
    CONSTRAINT ck_mk_engine_data_field_policy_channel CHECK (allowed_channel IN (
        'SERVICE_INTERNAL_ONLY','MASKED_OUTPUT_ONLY','AGGREGATE_ONLY','PUBLIC_METADATA_ONLY','FORBIDDEN'
    )),
    CONSTRAINT ck_mk_engine_data_field_policy_status CHECK (status IN ('ACTIVE','DEPRECATED'))
);

CREATE INDEX idx_mk_engine_data_encrypted_field_scope
    ON mk_engine_data_encrypted_field (tenant_id, scope_key);
CREATE INDEX idx_mk_engine_data_encrypted_field_hash
    ON mk_engine_data_encrypted_field (tenant_id, search_hash);
CREATE INDEX idx_mk_engine_data_field_policy_level
    ON mk_engine_data_field_policy (tenant_id, data_level, status);
CREATE INDEX idx_mk_engine_data_field_policy_status
    ON mk_engine_data_field_policy (tenant_id, status);

COMMENT ON TABLE mk_engine_data_encrypted_field IS 'DATASVC-01 D3/D4 字段级加密账本：只保存 SM4 密文、不可逆检索 hash 与审计锚点，不保存患者字段明文';
COMMENT ON COLUMN mk_engine_data_encrypted_field.scope_key IS '字段所属最小业务作用域，例如 clinical-context 或 export-request';
COMMENT ON COLUMN mk_engine_data_encrypted_field.field_name IS '加密字段名，不含字段明文值';
COMMENT ON COLUMN mk_engine_data_encrypted_field.cipher_text IS 'SM4 密文，禁止保存明文患者字段';
COMMENT ON COLUMN mk_engine_data_encrypted_field.search_hash IS '不可逆 SM3 检索 hash，用于等值匹配，不含明文';
COMMENT ON TABLE mk_engine_data_field_policy IS 'DATASVC-01 字段分级元数据：记录字段路径、数据级别、加密要求与允许通道';
COMMENT ON COLUMN mk_engine_data_field_policy.field_path IS '字段路径，由 scope_key.field_name 组成';
COMMENT ON COLUMN mk_engine_data_field_policy.encryption_required_flag IS '字段是否必须字段级加密；D3/D4 固定为 Y';
COMMENT ON COLUMN mk_engine_data_field_policy.allowed_channel IS '允许通道：仅服务内部、仅脱敏输出、仅聚合、公开元数据或禁用';
