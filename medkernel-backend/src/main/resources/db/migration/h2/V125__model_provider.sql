-- MedKernel 第二阶段 P2-A · LLM-08 模型 provider 真实接入配置（H2）
-- ROLLBACK：确认无引用后 DROP TABLE model_provider。credential_ref 仅存密钥引用，绝不落明文。

CREATE TABLE IF NOT EXISTS model_provider (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(64)   NOT NULL,
    provider_code   VARCHAR(64)   NOT NULL,
    provider_type   VARCHAR(32)   NOT NULL,
    endpoint_uri    VARCHAR(512)  NOT NULL,
    credential_ref  VARCHAR(128)  NULL,
    model_version   VARCHAR(64)   NULL,
    enabled_flag    CHAR(1)       NOT NULL DEFAULT 'Y',
    status          VARCHAR(32)   NOT NULL DEFAULT 'NOT_CONNECTED',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_model_provider_tenant_code UNIQUE (tenant_id, provider_code),
    CONSTRAINT ck_model_provider_type CHECK (provider_type IN ('OLLAMA', 'OPENAI_COMPATIBLE', 'CLAUDE', 'DIFY')),
    CONSTRAINT ck_model_provider_enabled CHECK (enabled_flag IN ('Y', 'N'))
);

CREATE INDEX idx_model_provider_tenant ON model_provider (tenant_id, provider_type);
