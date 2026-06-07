-- MedKernel v1.0 GA · 统一租户用户目录（达梦）
-- ROLLBACK：确认凭证、角色和身份绑定均已清理后，删除 tenant_user 表。

CREATE TABLE tenant_user (
    id           NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    VARCHAR2(64)  NOT NULL,
    user_id      VARCHAR2(128) NOT NULL,
    display_name VARCHAR2(128) NOT NULL,
    status       VARCHAR2(16)  DEFAULT 'ACTIVE' NOT NULL,
    version      NUMBER(19)    DEFAULT 1 NOT NULL,
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by   VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by   VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id     VARCHAR2(128) NULL,
    CONSTRAINT uk_tenant_user_identity UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_tenant_user_status CHECK (status IN ('ACTIVE','DISABLED','LOCKED')),
    CONSTRAINT ck_tenant_user_version CHECK (version >= 1)
);

CREATE INDEX idx_tenant_user_directory
    ON tenant_user (tenant_id, display_name, user_id);

INSERT INTO tenant_user
    (tenant_id, user_id, display_name, status, version,
     created_at, created_by, updated_at, updated_by, trace_id)
SELECT c.tenant_id, c.user_id, c.username, c.status, 1,
       c.created_at, c.created_by, c.updated_at, c.updated_by, c.trace_id
FROM platform_credential c;

INSERT INTO tenant_user
    (tenant_id, user_id, display_name, status, version,
     created_at, created_by, updated_at, updated_by, trace_id)
SELECT DISTINCT r.tenant_id, r.user_id, r.user_id, 'ACTIVE', 1,
       CURRENT_TIMESTAMP, 'migration-v96', CURRENT_TIMESTAMP, 'migration-v96', 'migration-v96'
FROM user_role_assignment r
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_user u
    WHERE u.tenant_id = r.tenant_id AND u.user_id = r.user_id
);

COMMENT ON TABLE tenant_user IS '租户用户唯一目录，凭证、角色和外部身份绑定共同引用该主体';
COMMENT ON COLUMN tenant_user.user_id IS '租户内稳定用户标识，不承载登录凭证';
COMMENT ON COLUMN tenant_user.display_name IS '面向管理员和临床人员展示的用户名称';
COMMENT ON COLUMN tenant_user.status IS '用户生命周期状态，ACTIVE、DISABLED 或 LOCKED';
