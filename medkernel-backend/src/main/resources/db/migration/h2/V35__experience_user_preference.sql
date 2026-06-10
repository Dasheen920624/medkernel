-- MedKernel v1.0 GA · BASE-10 用户体验偏好持久化（H2）

CREATE TABLE IF NOT EXISTS mk_experience_user_pref (
    user_pref_id VARCHAR(80)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL,
    user_id      VARCHAR(64)  NOT NULL,
    pref_key     VARCHAR(96)  NOT NULL,
    pref_value   CLOB         NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 1,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT pk_user_pref PRIMARY KEY (user_pref_id),
    CONSTRAINT uk_user_pref_user_key UNIQUE (tenant_id, user_id, pref_key),
    CONSTRAINT ck_user_pref_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);

CREATE INDEX idx_user_pref_user_key
    ON mk_experience_user_pref (tenant_id, user_id, pref_key, status);

COMMENT ON TABLE mk_experience_user_pref IS '系统用户体验偏好表：按租户、用户和偏好键保存主题等非临床 UI 偏好';
COMMENT ON COLUMN mk_experience_user_pref.pref_key IS '偏好键，例如 theme.mode';
COMMENT ON COLUMN mk_experience_user_pref.pref_value IS '偏好值（可为序列化 JSON，如通知设置），仅保存非敏感 UI 配置，不保存患者、令牌或密码信息';
