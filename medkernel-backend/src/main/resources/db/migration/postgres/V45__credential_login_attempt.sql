-- MedKernel AUTH-03 · 登录失败锁定与限流状态（PostgreSQL 15）
CREATE TABLE IF NOT EXISTS sys_login_attempt (
    id             BIGSERIAL    PRIMARY KEY,
    attempt_id     VARCHAR(64)  NOT NULL,
    tenant_id      VARCHAR(64)  NOT NULL,
    username       VARCHAR(128) NOT NULL,
    credential_id  VARCHAR(64),
    failed_count   INTEGER      NOT NULL DEFAULT 0,
    locked_until   TIMESTAMPTZ,
    last_failed_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id       VARCHAR(128),
    CONSTRAINT uk_sys_login_attempt_id UNIQUE (attempt_id),
    CONSTRAINT uk_sys_login_attempt_tenant_user UNIQUE (tenant_id, username),
    CONSTRAINT ck_sys_login_attempt_failed_count CHECK (failed_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sys_login_attempt_locked_until
    ON sys_login_attempt (tenant_id, locked_until);

COMMENT ON TABLE sys_login_attempt IS 'AUTH-03 登录失败计数与锁定限流状态表';
COMMENT ON COLUMN sys_login_attempt.failed_count IS '限流窗口内累计登录失败次数';
COMMENT ON COLUMN sys_login_attempt.locked_until IS '因失败锁定或限流建议解除时间';
