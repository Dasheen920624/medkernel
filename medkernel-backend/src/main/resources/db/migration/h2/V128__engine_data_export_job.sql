-- MedKernel 第二阶段 P2-B · DATASVC-01 引擎数据服务层异步导出作业（H2）
-- 三组 D2 去标识聚合读模型经 SYS-06 导出审批闸控制后异步 CSV 导出；审批锚点 approval_id/idempotency_key/request_snapshot。
-- ROLLBACK：确认无引用后 DROP TABLE mk_engine_data_export_job。

CREATE TABLE IF NOT EXISTS mk_engine_data_export_job (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    job_code         VARCHAR(64)   NOT NULL,
    requested_by     VARCHAR(64)   NOT NULL,
    export_type      VARCHAR(32)   NOT NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    progress         INTEGER       NOT NULL DEFAULT 0,
    result_uri       VARCHAR(512)  NULL,
    item_count       BIGINT        NULL,
    error_message    VARCHAR(1024) NULL,
    approval_id      VARCHAR(128)  NOT NULL,
    idempotency_key  VARCHAR(128)  NOT NULL,
    request_snapshot VARCHAR(2000) NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at       TIMESTAMP     NULL,
    completed_at     TIMESTAMP     NULL,
    expires_at       TIMESTAMP     NULL,
    CONSTRAINT uk_mk_engine_data_export_job_code UNIQUE (job_code),
    CONSTRAINT uk_mk_engine_data_export_job_idem UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_mk_engine_data_export_job_type CHECK (export_type IN ('RULE_USAGE', 'KNOWLEDGE_USAGE', 'CLINICAL_SIGNALS')),
    CONSTRAINT ck_mk_engine_data_export_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_mk_engine_data_export_job_tenant ON mk_engine_data_export_job (tenant_id, created_at);
