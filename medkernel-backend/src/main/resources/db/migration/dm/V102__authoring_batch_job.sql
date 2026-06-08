-- MedKernel v1.0 GA · P12-7 创作批量任务（达梦）
-- ROLLBACK：如需回滚，先导出 mk_engine_authoring_batch_job 与 mk_engine_authoring_batch_item，再删除两表。

CREATE TABLE mk_engine_authoring_batch_job (
    id                   NUMBER(19)    IDENTITY PRIMARY KEY,
    job_id               VARCHAR(128)  NOT NULL,
    tenant_id            VARCHAR(64)   NOT NULL,
    job_type             VARCHAR(32)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    total_count          NUMBER(10)    DEFAULT 0 NOT NULL,
    success_count        NUMBER(10)    DEFAULT 0 NOT NULL,
    failure_count        NUMBER(10)    DEFAULT 0 NOT NULL,
    retryable_count      NUMBER(10)    DEFAULT 0 NOT NULL,
    request_summary_json CLOB          NULL,
    result_summary_json  CLOB          NULL,
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by           VARCHAR(64)   DEFAULT 'system' NOT NULL,
    updated_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by           VARCHAR(64)   DEFAULT 'system' NOT NULL,
    trace_id             VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_engine_authoring_batch_job UNIQUE (tenant_id, job_id),
    CONSTRAINT ck_mk_engine_authoring_batch_job_type CHECK (job_type IN ('RULE_GENERATE','RULE_PUBLISH','PACKAGE_IMPORT','PACKAGE_EXPORT','PACKAGE_DISTRIBUTE')),
    CONSTRAINT ck_mk_engine_authoring_batch_job_status CHECK (status IN ('RUNNING','SUCCEEDED','PARTIAL_SUCCESS','FAILED','NOT_CONNECTED'))
);

CREATE TABLE mk_engine_authoring_batch_item (
    id              NUMBER(19)    IDENTITY PRIMARY KEY,
    job_id          VARCHAR(128)  NOT NULL,
    tenant_id       VARCHAR(64)   NOT NULL,
    item_id         VARCHAR(128)  NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    target_type     VARCHAR(64)   NULL,
    target_id       VARCHAR(128)  NULL,
    result_json     CLOB          NULL,
    rollback_ref    VARCHAR(128)  NULL,
    error_code      VARCHAR(64)   NULL,
    message         VARCHAR(500)  NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by      VARCHAR(64)   DEFAULT 'system' NOT NULL,
    trace_id        VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_engine_authoring_batch_item UNIQUE (tenant_id, job_id, item_id),
    CONSTRAINT ck_mk_engine_authoring_batch_item_status CHECK (status IN ('SUCCEEDED','FAILED','NOT_CONNECTED'))
);

CREATE INDEX idx_mk_engine_authoring_batch_job_status
    ON mk_engine_authoring_batch_job (tenant_id, status, created_at);

CREATE INDEX idx_mk_engine_authoring_batch_item_job
    ON mk_engine_authoring_batch_item (tenant_id, job_id);

CREATE INDEX idx_mk_engine_authoring_batch_item_status
    ON mk_engine_authoring_batch_item (tenant_id, status);

COMMENT ON TABLE mk_engine_authoring_batch_job IS '创作批量任务表：记录规则生成、规则发布、配置包导入导出和多目标分发的统一任务进度';
COMMENT ON COLUMN mk_engine_authoring_batch_job.job_id IS '批量任务业务 ID';
COMMENT ON COLUMN mk_engine_authoring_batch_job.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_authoring_batch_job.job_type IS '任务类型：规则生成、规则发布、包导入、包导出或包分发';
COMMENT ON COLUMN mk_engine_authoring_batch_job.status IS '任务状态：运行中、成功、部分成功、失败或目标未连接';
COMMENT ON COLUMN mk_engine_authoring_batch_job.total_count IS '任务逐项总数';
COMMENT ON COLUMN mk_engine_authoring_batch_job.success_count IS '成功项数量';
COMMENT ON COLUMN mk_engine_authoring_batch_job.failure_count IS '失败项数量';
COMMENT ON COLUMN mk_engine_authoring_batch_job.retryable_count IS '可重试未连接项数量';
COMMENT ON COLUMN mk_engine_authoring_batch_job.request_summary_json IS '请求摘要 JSON';
COMMENT ON COLUMN mk_engine_authoring_batch_job.result_summary_json IS '结果摘要 JSON';
COMMENT ON COLUMN mk_engine_authoring_batch_job.created_at IS '创建时间';
COMMENT ON COLUMN mk_engine_authoring_batch_job.created_by IS '创建人';
COMMENT ON COLUMN mk_engine_authoring_batch_job.updated_at IS '更新时间';
COMMENT ON COLUMN mk_engine_authoring_batch_job.updated_by IS '更新人';
COMMENT ON COLUMN mk_engine_authoring_batch_job.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE mk_engine_authoring_batch_item IS '创作批量任务逐项结果表：保存每行规则、配置包或同步目标的真实执行结果';
COMMENT ON COLUMN mk_engine_authoring_batch_item.job_id IS '批量任务业务 ID';
COMMENT ON COLUMN mk_engine_authoring_batch_item.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_authoring_batch_item.item_id IS '请求内逐项标识';
COMMENT ON COLUMN mk_engine_authoring_batch_item.status IS '逐项状态：成功、失败或目标未连接';
COMMENT ON COLUMN mk_engine_authoring_batch_item.target_type IS '目标类型：规则、配置包或同步目标';
COMMENT ON COLUMN mk_engine_authoring_batch_item.target_id IS '目标业务 ID';
COMMENT ON COLUMN mk_engine_authoring_batch_item.result_json IS '逐项成功或不可达结果 JSON';
COMMENT ON COLUMN mk_engine_authoring_batch_item.rollback_ref IS '发布计划或可回滚证据引用';
COMMENT ON COLUMN mk_engine_authoring_batch_item.error_code IS '失败错误码';
COMMENT ON COLUMN mk_engine_authoring_batch_item.message IS '逐项执行消息';
COMMENT ON COLUMN mk_engine_authoring_batch_item.created_at IS '记录时间';
COMMENT ON COLUMN mk_engine_authoring_batch_item.created_by IS '执行人';
COMMENT ON COLUMN mk_engine_authoring_batch_item.trace_id IS '请求链路追踪 ID';
