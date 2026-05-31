-- MedKernel v1.0 GA · CONFIG-01 配置中心（达梦 DM8）
CREATE TABLE mk_config_item (
    config_id      VARCHAR2(64)  NOT NULL,
    tenant_id      VARCHAR2(64)  NOT NULL,
    config_key     VARCHAR2(256) NOT NULL,
    config_value   CLOB          NOT NULL,
    value_type     VARCHAR2(32)  DEFAULT 'STRING' NOT NULL,
    display_name   VARCHAR2(128) NOT NULL,
    risk_level     VARCHAR2(16)  DEFAULT 'LOW' NOT NULL,
    owner          VARCHAR2(128) DEFAULT '信息科' NOT NULL,
    description    VARCHAR2(512),
    source         VARCHAR2(32)  DEFAULT 'YML_SEED' NOT NULL,
    protected_flag CHAR(1)       DEFAULT 'N' NOT NULL,
    active_flag    CHAR(1)       DEFAULT 'Y' NOT NULL,
    version        NUMBER(19)    DEFAULT 1 NOT NULL,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by     VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by     VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT pk_config_item PRIMARY KEY (config_id),
    CONSTRAINT uk_config_item_tenant_key UNIQUE (tenant_id, config_key),
    CONSTRAINT ck_config_item_value_type CHECK (value_type IN ('BOOLEAN','STRING','JSON','INTEGER','DECIMAL')),
    CONSTRAINT ck_config_item_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_config_item_source CHECK (source IN ('YML_SEED','DB','IMPORT','API')),
    CONSTRAINT ck_config_item_protected CHECK (protected_flag IN ('Y','N')),
    CONSTRAINT ck_config_item_active CHECK (active_flag IN ('Y','N'))
);

CREATE TABLE mk_config_history (
    history_id   VARCHAR2(80)  NOT NULL,
    tenant_id    VARCHAR2(64)  NOT NULL,
    config_key   VARCHAR2(256) NOT NULL,
    before_value CLOB,
    after_value  CLOB          NOT NULL,
    change_type  VARCHAR2(32)  NOT NULL,
    reason       VARCHAR2(512),
    version      NUMBER(19)    NOT NULL,
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by   VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT pk_config_history PRIMARY KEY (history_id),
    CONSTRAINT ck_config_history_change_type CHECK (change_type IN ('CREATE','UPDATE','ROLLBACK'))
);

CREATE INDEX idx_config_item_tenant_key
    ON mk_config_item (tenant_id, config_key, active_flag);

CREATE INDEX idx_config_history_tenant_key
    ON mk_config_history (tenant_id, config_key, created_at);

COMMENT ON TABLE mk_config_item IS '配置中心当前值表：保存可热生效的受控配置项、风险等级和保护标记';
COMMENT ON TABLE mk_config_history IS '配置中心变更历史表：追加记录配置变更前后值、原因、版本和操作人';
COMMENT ON COLUMN mk_config_item.config_key IS '配置键，全局命名空间使用点分层';
COMMENT ON COLUMN mk_config_item.protected_flag IS '保护配置标记：Y 表示高危或合规配置，需要额外护栏';
COMMENT ON COLUMN mk_config_history.reason IS '配置变更原因，支撑审计追溯';
