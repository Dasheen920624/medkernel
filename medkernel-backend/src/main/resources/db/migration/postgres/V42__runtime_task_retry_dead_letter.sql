-- MedKernel SYS-05 · 离线、重试与任务死信（PostgreSQL）
-- ROLLBACK: 如需回滚，先导出 sys_task_dead_letter 死信证据，再删除 V42 新增索引/表/列并恢复 V41 的模式与状态约束。

ALTER TABLE sys_task DROP CONSTRAINT IF EXISTS ck_sys_task_mode;
ALTER TABLE sys_task DROP CONSTRAINT IF EXISTS ck_sys_task_status;

ALTER TABLE sys_task ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE sys_task ADD COLUMN IF NOT EXISTS max_retries INTEGER DEFAULT 2 NOT NULL;
ALTER TABLE sys_task ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
ALTER TABLE sys_task ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(64);
ALTER TABLE sys_task ADD COLUMN IF NOT EXISTS dead_letter_id VARCHAR(64);
ALTER TABLE sys_task ADD COLUMN IF NOT EXISTS replayed_from_task_id VARCHAR(64);

ALTER TABLE sys_task ADD CONSTRAINT ck_sys_task_mode
    CHECK (task_mode IN ('ONLINE','ASYNC','BATCH','OFFLINE'));

ALTER TABLE sys_task ADD CONSTRAINT ck_sys_task_status
    CHECK (status IN ('UNREAD','PROCESSING','COMPLETED','PARTIAL_SUCCESS','FAILED','ESCALATED','NOT_CONNECTED','DEAD_LETTER'));

CREATE TABLE IF NOT EXISTS sys_task_dead_letter (
    id                   BIGSERIAL PRIMARY KEY,
    dead_letter_id       VARCHAR(64)  NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    org_path             VARCHAR(512),
    task_id              VARCHAR(64)  NOT NULL,
    task_mode            VARCHAR(16)  NOT NULL,
    task_type            VARCHAR(64)  NOT NULL,
    payload_storage_type VARCHAR(16),
    payload_uri          VARCHAR(512),
    payload_digest       VARCHAR(128),
    payload_size_bytes   BIGINT,
    total_count          INTEGER      NOT NULL DEFAULT 0,
    retry_count          INTEGER      NOT NULL DEFAULT 0,
    failure_details_json TEXT,
    error_code           VARCHAR(64),
    message              TEXT,
    trace_id             VARCHAR(128),
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(64)  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    updated_by           VARCHAR(64)  NOT NULL,
    replayed_at          TIMESTAMPTZ,
    replayed_by          VARCHAR(64),
    replay_task_id       VARCHAR(64),
    CONSTRAINT uk_sys_task_dead_letter UNIQUE (tenant_id, dead_letter_id),
    CONSTRAINT uk_sys_task_dead_task UNIQUE (tenant_id, task_id),
    CONSTRAINT ck_sys_task_dead_mode CHECK (task_mode IN ('ONLINE','ASYNC','BATCH','OFFLINE'))
);

CREATE INDEX IF NOT EXISTS idx_sys_task_retry_ts
    ON sys_task (tenant_id, status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_sys_task_dead_letter
    ON sys_task (tenant_id, dead_letter_id);

CREATE INDEX IF NOT EXISTS idx_sys_task_dead_tenant_ts
    ON sys_task_dead_letter (tenant_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sys_task_dead_task
    ON sys_task_dead_letter (task_id, tenant_id);

COMMENT ON COLUMN sys_task.retry_count IS '任务已执行的人工重试次数';
COMMENT ON COLUMN sys_task.max_retries IS '任务允许的最大人工重试次数';
COMMENT ON COLUMN sys_task.next_attempt_at IS '建议的下一次重试时间';
COMMENT ON COLUMN sys_task.dead_letter_id IS '任务进入死信后的死信 ID';
COMMENT ON COLUMN sys_task.replayed_from_task_id IS '死信回放来源任务 ID';
COMMENT ON TABLE sys_task_dead_letter IS 'SYS-05 任务死信表，保存重试耗尽任务的失败证据和人工回放结果';
COMMENT ON COLUMN sys_task_dead_letter.dead_letter_id IS '死信业务 ID，用于人工回放和审计追踪';
COMMENT ON COLUMN sys_task_dead_letter.task_id IS '进入死信的原始任务 ID';
COMMENT ON COLUMN sys_task_dead_letter.replay_task_id IS '人工回放后创建的新任务 ID';
