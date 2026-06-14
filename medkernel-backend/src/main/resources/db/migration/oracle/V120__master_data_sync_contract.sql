-- MedKernel v1.0 GA · 院内主数据同步批次、游标与来源版本台账（Oracle）
-- 回滚：确认外部主数据同步已停用且台账完成导出后，删除以下两张表。

CREATE TABLE mk_integration_master_data_sync_batch (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id          VARCHAR2(128) NOT NULL,
    tenant_id         VARCHAR2(64)  NOT NULL,
    webhook_id        VARCHAR2(128) NOT NULL,
    adapter_id        VARCHAR2(128) NOT NULL,
    source_system     VARCHAR2(64)  NOT NULL,
    sync_mode         VARCHAR2(32)  NOT NULL,
    previous_cursor   VARCHAR2(256) NULL,
    cursor_value      VARCHAR2(256) NOT NULL,
    payload_hash      VARCHAR2(64)  NOT NULL,
    status            VARCHAR2(16)  NOT NULL,
    total_count       NUMBER(10)    NOT NULL,
    applied_count     NUMBER(10)    NOT NULL,
    failed_count      NUMBER(10)    NOT NULL,
    error_summary     VARCHAR2(128) NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    processed_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    trace_id          VARCHAR2(128) NOT NULL,
    CONSTRAINT uk_mk_integration_master_data_sync_batch UNIQUE (tenant_id, source_system, batch_id),
    CONSTRAINT ck_mk_integration_master_data_sync_batch_mode CHECK (sync_mode IN ('INCREMENTAL', 'FULL_SNAPSHOT')),
    CONSTRAINT ck_mk_integration_master_data_sync_batch_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE TABLE mk_integration_master_data_sync_record (
    id                NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         VARCHAR2(64)  NOT NULL,
    source_system     VARCHAR2(64)  NOT NULL,
    resource_type     VARCHAR2(32)  NOT NULL,
    source_record_id  VARCHAR2(256) NOT NULL,
    internal_id       VARCHAR2(128) NOT NULL,
    source_version    NUMBER(19)    NOT NULL,
    payload_hash      VARCHAR2(64)  NOT NULL,
    status            VARCHAR2(16)  NOT NULL,
    last_batch_id     VARCHAR2(128) NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_mk_integration_master_data_sync_record UNIQUE (
        tenant_id, source_system, resource_type, source_record_id
    ),
    CONSTRAINT ck_mk_integration_master_data_sync_record_type CHECK (
        resource_type IN ('ORG_UNIT', 'PERSON', 'LOCAL_TERM')
    ),
    CONSTRAINT ck_mk_integration_master_data_sync_record_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_mk_integration_master_data_sync_record_version CHECK (source_version > 0)
);

CREATE INDEX idx_mk_integration_master_data_sync_batch_latest
    ON mk_integration_master_data_sync_batch (tenant_id, source_system, status, processed_at);
CREATE INDEX idx_mk_integration_master_data_sync_record_status
    ON mk_integration_master_data_sync_record (tenant_id, source_system, resource_type, status);

COMMENT ON TABLE mk_integration_master_data_sync_batch IS '院内主数据同步批次台账，保存验签后的批次摘要、连续游标和处理结果，不保存原始业务载荷';
COMMENT ON TABLE mk_integration_master_data_sync_record IS '来源主数据记录版本映射，保存外部记录到院内权威记录的版本、摘要和启停状态';
COMMENT ON COLUMN mk_integration_master_data_sync_batch.payload_hash IS '同步请求规范化内容的SHA-256摘要';
COMMENT ON COLUMN mk_integration_master_data_sync_batch.error_summary IS '失败错误码摘要，不保存人员或字典原始内容';
COMMENT ON COLUMN mk_integration_master_data_sync_record.payload_hash IS '单条来源记录规范化内容的SHA-256摘要';
COMMENT ON COLUMN mk_integration_master_data_sync_record.internal_id IS '院内关系库权威记录标识';
