-- MedKernel v1.0 GA · BASE-10 用户体验偏好持久化（人大金仓）

CREATE TABLE mk_experience_user_pref (
    user_pref_id VARCHAR(80)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL,
    user_id      VARCHAR(64)  NOT NULL,
    pref_key     VARCHAR(96)  NOT NULL,
    pref_value   TEXT         NOT NULL,
    version      BIGINT       DEFAULT 1 NOT NULL,
    status       VARCHAR(16)  DEFAULT 'ACTIVE' NOT NULL,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by   VARCHAR(64)  DEFAULT 'system' NOT NULL,
    updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by   VARCHAR(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT pk_user_pref PRIMARY KEY (user_pref_id),
    CONSTRAINT uk_user_pref_user_key UNIQUE (tenant_id, user_id, pref_key),
    CONSTRAINT ck_user_pref_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);

CREATE INDEX idx_user_pref_user_key
    ON mk_experience_user_pref (tenant_id, user_id, pref_key, status);

COMMENT ON TABLE mk_experience_user_pref IS '系统用户体验偏好表：按租户、用户和偏好键保存主题等非临床 UI 偏好';
COMMENT ON COLUMN mk_experience_user_pref.pref_key IS '偏好键，例如 theme.mode';
COMMENT ON COLUMN mk_experience_user_pref.pref_value IS '偏好值（可为序列化 JSON，如通知设置），仅保存非敏感 UI 配置，不保存患者、令牌或密码信息';
