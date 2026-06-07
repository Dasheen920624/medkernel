-- MedKernel v1.0 GA · GA-ENG-INTEG-01 第三方对接能力总线（PostgreSQL）

CREATE TABLE IF NOT EXISTS integration_adapter (
    id                  BIGSERIAL PRIMARY KEY,
    adapter_id          VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    name                VARCHAR(256)  NOT NULL,
    protocol_type       VARCHAR(64)   NOT NULL, -- HL7, FHIR, Webhook, REST, WebService
    status              VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED
    config_json         TEXT          NULL,
    health_status       VARCHAR(32)   NOT NULL DEFAULT 'HEALTHY', -- HEALTHY, UNHEALTHY
    rtt_ms              BIGINT        NOT NULL DEFAULT 0,
    last_heartbeat_at   TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_integration_adapter UNIQUE (adapter_id),
    CONSTRAINT ck_integration_adapter_status CHECK (status IN ('ACTIVE','SUSPENDED')),
    CONSTRAINT ck_integration_adapter_health CHECK (health_status IN ('HEALTHY','UNHEALTHY'))
);

CREATE INDEX IF NOT EXISTS idx_integ_adapter_tenant ON integration_adapter (tenant_id, protocol_type);

COMMENT ON TABLE integration_adapter IS '第三方系统接入适配器表';
COMMENT ON COLUMN integration_adapter.id IS '自增主键';
COMMENT ON COLUMN integration_adapter.adapter_id IS '适配器全局唯一ID';
COMMENT ON COLUMN integration_adapter.tenant_id IS '租户ID';
COMMENT ON COLUMN integration_adapter.name IS '适配器中文系统名称';
COMMENT ON COLUMN integration_adapter.protocol_type IS '接口协议类型 (HL7, FHIR, Webhook, REST, WebService)';
COMMENT ON COLUMN integration_adapter.status IS '适配器启用状态 (ACTIVE, SUSPENDED)';
COMMENT ON COLUMN integration_adapter.config_json IS '连接具体配置与字段映射映射规则 Json 结构';
COMMENT ON COLUMN integration_adapter.health_status IS '健康诊断状态 (HEALTHY, UNHEALTHY)';
COMMENT ON COLUMN integration_adapter.rtt_ms IS '心跳握手单向延迟毫秒数';
COMMENT ON COLUMN integration_adapter.last_heartbeat_at IS '最后一次成功健康自检握手时间';

CREATE TABLE IF NOT EXISTS integration_webhook_config (
    id                  BIGSERIAL PRIMARY KEY,
    webhook_id          VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    name                VARCHAR(256)  NOT NULL,
    callback_url        VARCHAR(512)  NOT NULL,
    secret_cipher       VARCHAR(256)  NOT NULL,
    events_subscribed   VARCHAR(512)  NOT NULL, -- 例如 OUTPATIENT_DIAGNOSIS, ADMISSION_CHECK, DISCHARGE_PLAN
    status              VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_integration_webhook UNIQUE (webhook_id),
    CONSTRAINT ck_integration_webhook_status CHECK (status IN ('ACTIVE','SUSPENDED'))
);

CREATE INDEX IF NOT EXISTS idx_integ_webhook_tenant ON integration_webhook_config (tenant_id);

COMMENT ON TABLE integration_webhook_config IS 'Webhook订阅回调通知安全配置表';
COMMENT ON COLUMN integration_webhook_config.id IS '自增主键';
COMMENT ON COLUMN integration_webhook_config.webhook_id IS 'Webhook订阅配置全局唯一ID';
COMMENT ON COLUMN integration_webhook_config.tenant_id IS '租户ID';
COMMENT ON COLUMN integration_webhook_config.name IS '订阅名称';
COMMENT ON COLUMN integration_webhook_config.callback_url IS '目标系统的通知回调物理地址';
COMMENT ON COLUMN integration_webhook_config.secret_cipher IS '用于生成 HMAC-SHA256 签名的共享密钥 SM4 密文';
COMMENT ON COLUMN integration_webhook_config.events_subscribed IS '已订阅场景事件列表 (逗号分隔)';
COMMENT ON COLUMN integration_webhook_config.status IS '订阅状态 (ACTIVE, SUSPENDED)';

CREATE TABLE IF NOT EXISTS integration_message_log (
    id                  BIGSERIAL PRIMARY KEY,
    message_id          VARCHAR(64)   NOT NULL,
    tenant_id           VARCHAR(64)   NOT NULL,
    trace_id            VARCHAR(128)  NULL,
    direction           VARCHAR(32)   NOT NULL, -- INBOUND, OUTBOUND
    system_name         VARCHAR(128)  NOT NULL,
    protocol_type       VARCHAR(32)   NOT NULL,
    payload_summary     VARCHAR(512)  NULL,
    payload             TEXT          NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'SUCCESS', -- SUCCESS, FAILED, RETRYING, DEAD_LETTER
    retry_count         INTEGER       NOT NULL DEFAULT 0,
    max_retries         INTEGER       NOT NULL DEFAULT 3,
    error_message       VARCHAR(512)  NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_integration_message UNIQUE (message_id),
    CONSTRAINT ck_integration_message_dir CHECK (direction IN ('INBOUND','OUTBOUND')),
    CONSTRAINT ck_integration_message_status CHECK (status IN ('SUCCESS','FAILED','RETRYING','DEAD_LETTER'))
);

CREATE INDEX IF NOT EXISTS idx_integ_msg_tenant ON integration_message_log (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_integ_msg_trace ON integration_message_log (trace_id);

COMMENT ON TABLE integration_message_log IS '接口对接数据流与重试死信队列凭证日志表';
COMMENT ON COLUMN integration_message_log.id IS '自增主键';
COMMENT ON COLUMN integration_message_log.message_id IS '消息审计全局唯一ID';
COMMENT ON COLUMN integration_message_log.tenant_id IS '租户ID';
COMMENT ON COLUMN integration_message_log.trace_id IS '请求全链路追踪 traceId';
COMMENT ON COLUMN integration_message_log.direction IS '对接数据方向 (INBOUND, OUTBOUND)';
COMMENT ON COLUMN integration_message_log.system_name IS '第三方系统中文标识名称';
COMMENT ON COLUMN integration_message_log.protocol_type IS '传输协议类型';
COMMENT ON COLUMN integration_message_log.payload_summary IS '消息 Payload 格式化简要内容摘要';
COMMENT ON COLUMN integration_message_log.payload IS '数据交换报文 Payload 完整明文内容';
COMMENT ON COLUMN integration_message_log.status IS '数据流审计及重试队列状态 (SUCCESS, FAILED, RETRYING, DEAD_LETTER)';
COMMENT ON COLUMN integration_message_log.retry_count IS '当前已重试调用发送次数';
COMMENT ON COLUMN integration_message_log.max_retries IS '设计最大允许重试次数门槛';
COMMENT ON COLUMN integration_message_log.error_message IS '最近一次交互失败时返回的错误细节及堆栈摘要';
