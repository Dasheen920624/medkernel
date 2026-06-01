-- MedKernel AUTH-03 · MFA / SM3 / 受控重置（人大金仓）
ALTER TABLE platform_credential ALTER COLUMN password_hash TYPE VARCHAR(255);
ALTER TABLE platform_credential ALTER COLUMN mfa_secret TYPE VARCHAR(512);

CREATE TABLE IF NOT EXISTS sys_password_reset_token (
    id          BIGSERIAL    PRIMARY KEY,
    reset_id    VARCHAR(64)  NOT NULL,
    tenant_id   VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(128) NOT NULL,
    username    VARCHAR(128) NOT NULL,
    token_hash  VARCHAR(96)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id    VARCHAR(128),
    CONSTRAINT uk_password_reset_token_id UNIQUE (reset_id),
    CONSTRAINT ck_password_reset_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS idx_pwd_reset_token_lookup
    ON sys_password_reset_token (tenant_id, user_id, token_hash, used_at);

CREATE INDEX IF NOT EXISTS idx_pwd_reset_token_expiry
    ON sys_password_reset_token (tenant_id, expires_at);

COMMENT ON TABLE sys_password_reset_token IS 'AUTH-03 受控密码重置一次性 token 表';
COMMENT ON COLUMN sys_password_reset_token.token_hash IS '一次性重置 token 的 SM3 摘要，明文仅返回一次';
COMMENT ON COLUMN sys_password_reset_token.used_at IS 'token 被消费的时间；非空表示不可再次使用';
