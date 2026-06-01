-- MedKernel AUTH-03 · MFA / SM3 / 受控重置（达梦 DM8）
ALTER TABLE platform_credential MODIFY password_hash VARCHAR2(255);
ALTER TABLE platform_credential MODIFY mfa_secret VARCHAR2(512);

CREATE TABLE sys_password_reset_token (
    id          NUMBER(19)    IDENTITY PRIMARY KEY,
    reset_id    VARCHAR2(64)  NOT NULL,
    tenant_id   VARCHAR2(64)  NOT NULL,
    user_id     VARCHAR2(128) NOT NULL,
    username    VARCHAR2(128) NOT NULL,
    token_hash  VARCHAR2(96)  NOT NULL,
    expires_at  TIMESTAMP     NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by  VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by  VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id    VARCHAR2(128),
    CONSTRAINT uk_password_reset_token_id UNIQUE (reset_id),
    CONSTRAINT ck_password_reset_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_pwd_reset_token_lookup
    ON sys_password_reset_token (tenant_id, user_id, token_hash, used_at);

CREATE INDEX idx_pwd_reset_token_expiry
    ON sys_password_reset_token (tenant_id, expires_at);

COMMENT ON TABLE sys_password_reset_token IS 'AUTH-03 受控密码重置一次性 token 表';
COMMENT ON COLUMN sys_password_reset_token.token_hash IS '一次性重置 token 的 SM3 摘要，明文仅返回一次';
COMMENT ON COLUMN sys_password_reset_token.used_at IS 'token 被消费的时间；非空表示不可再次使用';
