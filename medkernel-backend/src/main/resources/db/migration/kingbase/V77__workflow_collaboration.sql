-- MedKernel v1.0 GA · SVC-CLINICAL-03 临床协同待办与通知中心（人大金仓）
-- ROLLBACK：如需回滚，先导出 mk_engine_workflow_todo 与 mk_engine_notification 审计证据，再删除两张新增表。

CREATE TABLE IF NOT EXISTS mk_engine_workflow_todo (
    id                  BIGSERIAL PRIMARY KEY,
    todo_id             VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    source_type         VARCHAR(32)  NOT NULL,
    source_id           VARCHAR(128) NOT NULL,
    title               VARCHAR(256) NOT NULL,
    summary             TEXT         NOT NULL,
    priority            VARCHAR(16)  NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    assignee_id         VARCHAR(64)  NULL,
    assignee_role       VARCHAR(64)  NULL,
    patient_id          VARCHAR(128) NULL,
    encounter_id        VARCHAR(128) NULL,
    due_at              TIMESTAMPTZ  NULL,
    deep_link           VARCHAR(512) NULL,
    completion_reason   TEXT         NULL,
    completed_at        TIMESTAMPTZ  NULL,
    completed_by        VARCHAR(64)  NULL,
    transferred_to      VARCHAR(64)  NULL,
    trace_id            VARCHAR(128) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_workflow_todo_id UNIQUE (todo_id),
    CONSTRAINT uk_workflow_todo_source UNIQUE (tenant_id, source_type, source_id),
    CONSTRAINT ck_workflow_todo_source_type CHECK (source_type IN (
        'FOLLOWUP_TASK','SAFETY_REVIEW','RECOMMENDATION_CARD','NURSING_TASK','REPORT_INTERPRETATION','BEDSIDE_KNOWLEDGE','PATHWAY_NODE'
    )),
    CONSTRAINT ck_workflow_todo_priority CHECK (priority IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    CONSTRAINT ck_workflow_todo_status CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','TRANSFERRED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_workflow_todo_tenant_status_due
    ON mk_engine_workflow_todo (tenant_id, status, due_at);
CREATE INDEX IF NOT EXISTS idx_workflow_todo_assignee_status
    ON mk_engine_workflow_todo (tenant_id, assignee_id, status);

CREATE TABLE IF NOT EXISTS mk_engine_notification (
    id                  BIGSERIAL PRIMARY KEY,
    notification_id     VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    source_type         VARCHAR(32)  NOT NULL,
    source_id           VARCHAR(128) NOT NULL,
    dedupe_key          VARCHAR(256) NOT NULL,
    title               VARCHAR(256) NOT NULL,
    message             TEXT         NOT NULL,
    notification_level  VARCHAR(16)  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'UNREAD',
    recipient_id        VARCHAR(64)  NULL,
    recipient_role      VARCHAR(64)  NULL,
    patient_id          VARCHAR(128) NULL,
    encounter_id        VARCHAR(128) NULL,
    deep_link           VARCHAR(512) NULL,
    read_at             TIMESTAMPTZ  NULL,
    read_by             VARCHAR(64)  NULL,
    trace_id            VARCHAR(128) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_notification_id UNIQUE (notification_id),
    CONSTRAINT uk_notification_dedupe UNIQUE (tenant_id, dedupe_key),
    CONSTRAINT ck_notification_source_type CHECK (source_type IN ('FOLLOWUP_EVENT','SAFETY_REVIEW','WORKFLOW_TODO')),
    CONSTRAINT ck_notification_level CHECK (notification_level IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
    CONSTRAINT ck_notification_status CHECK (status IN ('UNREAD','READ'))
);

CREATE INDEX IF NOT EXISTS idx_notification_tenant_status_created
    ON mk_engine_notification (tenant_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_notification_recipient_status
    ON mk_engine_notification (tenant_id, recipient_id, status);

COMMENT ON TABLE mk_engine_workflow_todo IS 'SVC-CLINICAL-03 临床协同待办表，统一承接随访、安全撤回、路径节点和后续护理/床旁知识等真实来源任务';
COMMENT ON COLUMN mk_engine_workflow_todo.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_workflow_todo.source_type IS '待办来源类型';
COMMENT ON COLUMN mk_engine_workflow_todo.source_id IS '来源业务 ID';
COMMENT ON COLUMN mk_engine_workflow_todo.priority IS '待办优先级';
COMMENT ON COLUMN mk_engine_workflow_todo.status IS '待办办理状态';
COMMENT ON COLUMN mk_engine_workflow_todo.completion_reason IS '完成说明';
COMMENT ON COLUMN mk_engine_workflow_todo.trace_id IS '链路追踪 ID';

COMMENT ON TABLE mk_engine_notification IS 'SVC-CLINICAL-03 通知中心表，统一承接真实业务事件并保存已读状态';
COMMENT ON COLUMN mk_engine_notification.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_notification.source_type IS '通知来源类型';
COMMENT ON COLUMN mk_engine_notification.dedupe_key IS '通知去重键';
COMMENT ON COLUMN mk_engine_notification.notification_level IS '通知级别';
COMMENT ON COLUMN mk_engine_notification.status IS '阅读状态';
COMMENT ON COLUMN mk_engine_notification.read_by IS '阅读人';
COMMENT ON COLUMN mk_engine_notification.trace_id IS '链路追踪 ID';
