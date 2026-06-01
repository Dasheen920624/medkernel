-- MedKernel v1.0 GA · GA-ENG-API-13 大规模列表 API（H2 baseline，MODE=PostgreSQL 兼容）
-- BASE-08 对齐：异步导出任务统一落 mk_experience_export_task。

CREATE TABLE IF NOT EXISTS mk_experience_export_task (
    task_id          VARCHAR(80)   NOT NULL,
    tenant_id        VARCHAR(64)   NOT NULL,
    resource_type    VARCHAR(64)   NOT NULL,
    request_snapshot TEXT          NOT NULL,
    selected_scope   VARCHAR(32)   NOT NULL DEFAULT 'FILTERED_RESULT',
    status           VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    file_name        VARCHAR(255),
    file_path        VARCHAR(512),
    file_size        BIGINT        NOT NULL DEFAULT 0,
    error_message    VARCHAR(512),
    time_cost_ms     BIGINT        NOT NULL DEFAULT 0,
    trace_id         VARCHAR(128),
    audit_id         VARCHAR(128),
    idempotency_key  VARCHAR(128),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT pk_export_task PRIMARY KEY (task_id),
    CONSTRAINT uk_export_task_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_export_task_scope CHECK (selected_scope IN ('CURRENT_PAGE','FILTERED_RESULT')),
    CONSTRAINT ck_export_task_status CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','EXPIRED'))
);

CREATE INDEX idx_export_task_status
    ON mk_experience_export_task (tenant_id, status, created_at);

CREATE INDEX idx_export_task_resource
    ON mk_experience_export_task (tenant_id, resource_type, created_at);

COMMENT ON TABLE mk_experience_export_task IS '系统异步导出任务表：保存大规模列表导出任务、视图快照、物理文件与审计线索';
COMMENT ON COLUMN mk_experience_export_task.task_id IS '异步导出任务全局唯一 ID';
COMMENT ON COLUMN mk_experience_export_task.request_snapshot IS '导出时的页面视图、筛选、列和选择范围快照 JSON';
COMMENT ON COLUMN mk_experience_export_task.selected_scope IS '导出范围：CURRENT_PAGE 当前页 / FILTERED_RESULT 筛选结果';
COMMENT ON COLUMN mk_experience_export_task.idempotency_key IS '幂等键，防止重复提交同一导出任务';
