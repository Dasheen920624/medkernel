-- MedKernel v1.0 GA · GA-ENG-API-13 大规模列表 API（达梦 DM8）
-- BASE-08 对齐：异步导出任务统一落 mk_experience_export_task。

CREATE TABLE mk_experience_export_task (
    task_id          VARCHAR2(80)  NOT NULL,
    tenant_id        VARCHAR2(64)  NOT NULL,
    resource_type    VARCHAR2(64)  NOT NULL,
    request_snapshot CLOB          NOT NULL,
    selected_scope   VARCHAR2(32)  DEFAULT 'FILTERED_RESULT' NOT NULL,
    status           VARCHAR2(32)  DEFAULT 'PENDING' NOT NULL,
    file_name        VARCHAR2(255),
    file_path        VARCHAR2(512),
    file_size        NUMBER(19)    DEFAULT 0 NOT NULL,
    error_message    VARCHAR2(512),
    time_cost_ms     NUMBER(19)    DEFAULT 0 NOT NULL,
    trace_id         VARCHAR2(128),
    audit_id         VARCHAR2(128),
    idempotency_key  VARCHAR2(128),
    created_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by       VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by       VARCHAR2(64)  DEFAULT 'system' NOT NULL,
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
