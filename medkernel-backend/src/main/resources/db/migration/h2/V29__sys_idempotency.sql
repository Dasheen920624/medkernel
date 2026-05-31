-- MedKernel v1.0 GA · 平台级幂等记录表（H2 2.2）
CREATE TABLE IF NOT EXISTS sys_idempotency (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id             VARCHAR(64)  NOT NULL,
    idempotency_key       VARCHAR(128) NOT NULL,
    request_method        VARCHAR(16)  NOT NULL,
    request_path          VARCHAR(512) NOT NULL,
    request_hash          VARCHAR(64)  NOT NULL,
    response_status       INTEGER,
    response_content_type VARCHAR(128),
    response_body         CLOB,
    result_hash           VARCHAR(64),
    trace_id              VARCHAR(128),
    status                VARCHAR(16)  NOT NULL DEFAULT 'PROCESSING',
    expires_at            TIMESTAMP    NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_idempotency_tenant_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_sys_idempotency_status CHECK (status IN ('PROCESSING','COMPLETED'))
);

CREATE INDEX IF NOT EXISTS idx_sys_idempotency_expiry
    ON sys_idempotency (status, expires_at);

COMMENT ON TABLE sys_idempotency IS '平台级幂等记录表：保存 Idempotency-Key 首次成功响应，防止写操作重复副作用';
COMMENT ON COLUMN sys_idempotency.tenant_id IS '租户 ID';
COMMENT ON COLUMN sys_idempotency.idempotency_key IS '幂等键，来自 Idempotency-Key 请求头';
COMMENT ON COLUMN sys_idempotency.request_method IS '首次请求 HTTP 方法';
COMMENT ON COLUMN sys_idempotency.request_path IS '首次请求路径与查询串';
COMMENT ON COLUMN sys_idempotency.request_hash IS '首次请求摘要，用于拒绝同键异文';
COMMENT ON COLUMN sys_idempotency.response_status IS '首次成功响应 HTTP 状态码';
COMMENT ON COLUMN sys_idempotency.response_content_type IS '首次成功响应媒体类型';
COMMENT ON COLUMN sys_idempotency.response_body IS '首次成功响应体';
COMMENT ON COLUMN sys_idempotency.result_hash IS '首次成功响应体 SHA-256 摘要';
COMMENT ON COLUMN sys_idempotency.trace_id IS '首次请求 traceId';
COMMENT ON COLUMN sys_idempotency.status IS '幂等状态：PROCESSING 处理中 / COMPLETED 已完成';
COMMENT ON COLUMN sys_idempotency.expires_at IS '幂等记录过期时间，用于 TTL 清理';
