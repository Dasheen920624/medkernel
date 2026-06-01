-- MedKernel AUTH-03 · 登录失败锁定与限流状态（达梦 DM8）
CREATE TABLE sys_login_attempt (
    id             NUMBER(19)    IDENTITY PRIMARY KEY,
    attempt_id     VARCHAR2(64)  NOT NULL,
    tenant_id      VARCHAR2(64)  NOT NULL,
    username       VARCHAR2(128) NOT NULL,
    credential_id  VARCHAR2(64),
    failed_count   NUMBER(10)    DEFAULT 0 NOT NULL,
    locked_until   TIMESTAMP,
    last_failed_at TIMESTAMP,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by     VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by     VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id       VARCHAR2(128),
    CONSTRAINT uk_sys_login_attempt_id UNIQUE (attempt_id),
    CONSTRAINT uk_sys_login_attempt_tenant_user UNIQUE (tenant_id, username),
    CONSTRAINT ck_sys_login_attempt_failed_count CHECK (failed_count >= 0)
);

CREATE INDEX idx_sys_login_attempt_locked_until
    ON sys_login_attempt (tenant_id, locked_until);

COMMENT ON TABLE sys_login_attempt IS 'AUTH-03 登录失败计数与锁定限流状态表';
COMMENT ON COLUMN sys_login_attempt.failed_count IS '限流窗口内累计登录失败次数';
COMMENT ON COLUMN sys_login_attempt.locked_until IS '因失败锁定或限流建议解除时间';
