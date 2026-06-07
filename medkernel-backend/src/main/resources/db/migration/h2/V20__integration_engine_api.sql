-- MedKernel v1.0 GA · GA-ENG-INTEG-01 第三方对接能力总线（H2 PostgreSQL 兼容模式）

CREATE TABLE IF NOT EXISTS integration_adapter (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    adapter_id          VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    name                VARCHAR(256)  NOT NULL,
    protocol_type       VARCHAR(64)   NOT NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    config_json         VARCHAR(4000) NULL,
    health_status       VARCHAR(32)   NOT NULL DEFAULT 'HEALTHY',
    rtt_ms              BIGINT        NOT NULL DEFAULT 0,
    last_heartbeat_at   TIMESTAMP     NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_integration_adapter UNIQUE (adapter_id),
    CONSTRAINT ck_integration_adapter_status CHECK (status IN ('ACTIVE','SUSPENDED')),
    CONSTRAINT ck_integration_adapter_health CHECK (health_status IN ('HEALTHY','UNHEALTHY'))
);

CREATE INDEX IF NOT EXISTS idx_integ_adapter_tenant ON integration_adapter (tenant_id, protocol_type);

CREATE TABLE IF NOT EXISTS integration_webhook_config (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    webhook_id          VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    name                VARCHAR(256)  NOT NULL,
    callback_url        VARCHAR(512)  NOT NULL,
    secret_cipher       VARCHAR(256)  NOT NULL,
    events_subscribed   VARCHAR(512)  NOT NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_integration_webhook UNIQUE (webhook_id),
    CONSTRAINT ck_integration_webhook_status CHECK (status IN ('ACTIVE','SUSPENDED'))
);

CREATE INDEX IF NOT EXISTS idx_integ_webhook_tenant ON integration_webhook_config (tenant_id);

CREATE TABLE IF NOT EXISTS integration_message_log (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id          VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    trace_id            VARCHAR(128)  NULL,
    direction           VARCHAR(32)   NOT NULL,
    system_name         VARCHAR(128)  NOT NULL,
    protocol_type       VARCHAR(32)   NOT NULL,
    payload_summary     VARCHAR(512)  NULL,
    payload             VARCHAR(4000) NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'SUCCESS',
    retry_count         INTEGER       NOT NULL DEFAULT 0,
    max_retries         INTEGER       NOT NULL DEFAULT 3,
    error_message       VARCHAR(512)  NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_integration_message UNIQUE (message_id),
    CONSTRAINT ck_integration_message_dir CHECK (direction IN ('INBOUND','OUTBOUND')),
    CONSTRAINT ck_integration_message_status CHECK (status IN ('SUCCESS','FAILED','RETRYING','DEAD_LETTER'))
);

CREATE INDEX IF NOT EXISTS idx_integ_msg_tenant ON integration_message_log (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_integ_msg_trace ON integration_message_log (trace_id);
