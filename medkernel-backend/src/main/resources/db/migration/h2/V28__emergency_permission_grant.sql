-- MedKernel v1.0 GA · 应急权限授予表（H2 2.2）
CREATE TABLE IF NOT EXISTS emergency_permission_grant (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(128) NOT NULL,
    permission_code VARCHAR(128) NOT NULL DEFAULT 'env.emergency',
    reason          VARCHAR(512) NOT NULL,
    granted_by      VARCHAR(128) NOT NULL,
    granted_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP    NOT NULL,
    revoked_at      TIMESTAMP,
    revoked_by      VARCHAR(128),
    active_flag     CHAR(1)      NOT NULL DEFAULT 'Y',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_emergency_permission_code CHECK (permission_code = 'env.emergency'),
    CONSTRAINT ck_emergency_permission_active CHECK (active_flag IN ('Y','N'))
);

CREATE INDEX IF NOT EXISTS idx_emergency_permission_active
    ON emergency_permission_grant (tenant_id, user_id, permission_code, active_flag, expires_at);

CREATE INDEX IF NOT EXISTS idx_emergency_permission_expiry
    ON emergency_permission_grant (active_flag, expires_at);

COMMENT ON TABLE emergency_permission_grant IS '应急权限授予记录表，break-glass 权限必须审计且到期自动失效';
