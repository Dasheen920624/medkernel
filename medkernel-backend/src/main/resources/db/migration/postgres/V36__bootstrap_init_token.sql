-- MedKernel v1.0 GA · BASE-11 首发部署 init token（PostgreSQL）

CREATE TABLE IF NOT EXISTS mk_security_bootstrap_init_token (
    id          BIGSERIAL   PRIMARY KEY,
    token_id    VARCHAR(80) NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    used_by     VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(64) NOT NULL DEFAULT 'system',
    trace_id    VARCHAR(64),
    CONSTRAINT uk_bootstrap_init_token_id UNIQUE (token_id),
    CONSTRAINT uk_bootstrap_init_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_bootstrap_init_token_status CHECK (status IN ('ACTIVE','USED','REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_bootstrap_init_token_expires
    ON mk_security_bootstrap_init_token (status, expires_at);

COMMENT ON TABLE mk_security_bootstrap_init_token IS '首发部署一次性 init token：仅保存 SHA-256 摘要，用于全新生产环境安全接管';
COMMENT ON COLUMN mk_security_bootstrap_init_token.token_hash IS 'init token 的 SHA-256 摘要，禁止保存明文 token';
COMMENT ON COLUMN mk_security_bootstrap_init_token.expires_at IS 'token 过期时间，过期后必须诚实拒绝';
COMMENT ON COLUMN mk_security_bootstrap_init_token.used_at IS 'token 首次消费时间，非空表示已使用';
