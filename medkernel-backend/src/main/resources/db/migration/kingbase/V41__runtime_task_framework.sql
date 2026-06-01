-- MedKernel SYS-05 · 在线 / 异步 / 批量运行任务框架（人大金仓）

CREATE TABLE IF NOT EXISTS sys_task (
    id                   BIGSERIAL PRIMARY KEY,
    task_id              VARCHAR(64)  NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    org_path             VARCHAR(512),
    task_mode            VARCHAR(16)  NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    task_type            VARCHAR(64)  NOT NULL,
    payload_storage_type VARCHAR(16),
    payload_uri          VARCHAR(512),
    payload_digest       VARCHAR(128),
    payload_size_bytes   BIGINT,
    total_count          INTEGER      NOT NULL DEFAULT 0,
    success_count        INTEGER      NOT NULL DEFAULT 0,
    failure_count        INTEGER      NOT NULL DEFAULT 0,
    retryable_count      INTEGER      NOT NULL DEFAULT 0,
    failure_details_json TEXT,
    message              TEXT,
    error_code           VARCHAR(64),
    trace_id             VARCHAR(128),
    started_at           TIMESTAMPTZ,
    finished_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(64)  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    updated_by           VARCHAR(64)  NOT NULL,
    CONSTRAINT uk_sys_task_tenant_task UNIQUE (tenant_id, task_id),
    CONSTRAINT ck_sys_task_mode CHECK (task_mode IN ('ONLINE','ASYNC','BATCH')),
    CONSTRAINT ck_sys_task_status CHECK (status IN ('UNREAD','PROCESSING','COMPLETED','PARTIAL_SUCCESS','FAILED','ESCALATED'))
);

CREATE INDEX IF NOT EXISTS idx_sys_task_status_ts
    ON sys_task (tenant_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_sys_task_mode_ts
    ON sys_task (tenant_id, task_mode, created_at);

CREATE INDEX IF NOT EXISTS idx_sys_task_org_ts
    ON sys_task (tenant_id, org_path, created_at);

COMMENT ON TABLE sys_task IS 'SYS-05 任务运行框架表，承载在线、异步、批量任务的权威状态';
COMMENT ON COLUMN sys_task.task_id IS '任务业务 ID，用于轮询和审计';
COMMENT ON COLUMN sys_task.task_mode IS '任务运行模式：在线、异步或批量';
COMMENT ON COLUMN sys_task.status IS '待办状态机状态：未读、处理中、完成、部分成功、失败或升级';
COMMENT ON COLUMN sys_task.failure_details_json IS '批量任务失败明细 JSON，记录错误码、原因和是否可重试';
