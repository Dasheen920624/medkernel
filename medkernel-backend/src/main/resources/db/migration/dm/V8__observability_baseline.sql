-- MedKernel v1.0 GA · OBS-01 可观测性骨干迁移
-- 1. mk_obs_state_transition：所有引擎实体状态机跳转的统一历史
-- 2. mk_obs_payload_store：所有引擎大 payload 的统一旁路存储
-- 3. canonical_resource 加 trace_id，供上下文资源追溯

CREATE TABLE mk_obs_state_transition (
    id              NUMBER(19)    IDENTITY PRIMARY KEY,
    entity_type     VARCHAR2(64)  NOT NULL,
    entity_id       VARCHAR2(128) NOT NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    org_path        VARCHAR2(512) NULL,
    from_status     VARCHAR2(64)  NULL,
    to_status       VARCHAR2(64)  NOT NULL,
    reason          VARCHAR2(128) NOT NULL,
    actor           VARCHAR2(64)  NULL,
    trace_id        VARCHAR2(128) NULL,
    error_code      VARCHAR2(64)  NULL,
    error_class     VARCHAR2(32)  NULL,
    error_message   VARCHAR2(512) NULL,
    retry_count     NUMBER(10)    NULL,
    next_retry_at   TIMESTAMP     NULL,
    occurred_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by      VARCHAR2(64)  NULL,
    CONSTRAINT ck_most_error_class CHECK (error_class IS NULL OR error_class IN ('INPUT','AUTH','DATA','EXTERNAL','INTERNAL'))
);

CREATE INDEX idx_most_entity      ON mk_obs_state_transition (entity_type, entity_id, occurred_at);
CREATE INDEX idx_most_tenant_time ON mk_obs_state_transition (tenant_id, occurred_at);
CREATE INDEX idx_most_trace       ON mk_obs_state_transition (trace_id);
CREATE INDEX idx_most_failed      ON mk_obs_state_transition (tenant_id, error_class, occurred_at);

CREATE TABLE mk_obs_payload_store (
    id              NUMBER(19)    IDENTITY PRIMARY KEY,
    payload_id      VARCHAR2(64)  NOT NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    org_path        VARCHAR2(512) NULL,
    entity_type     VARCHAR2(64)  NOT NULL,
    entity_id       VARCHAR2(128) NOT NULL,
    trace_id        VARCHAR2(128) NULL,
    storage_type    VARCHAR2(16)  DEFAULT 'INLINE' NOT NULL,
    content_type    VARCHAR2(128) DEFAULT 'application/octet-stream' NOT NULL,
    digest          VARCHAR2(128) NOT NULL,
    size_bytes      NUMBER(19)    NOT NULL,
    payload_base64  CLOB          NULL,
    payload_uri     VARCHAR2(512) NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by      VARCHAR2(64)  NULL,
    deleted_at      TIMESTAMP     NULL,
    deleted_by      VARCHAR2(64)  NULL,
    CONSTRAINT uk_mops_payload_id UNIQUE (payload_id),
    CONSTRAINT ck_mops_storage_type CHECK (storage_type IN ('INLINE','URI'))
);

CREATE INDEX idx_mops_trace       ON mk_obs_payload_store (trace_id, created_at);
CREATE INDEX idx_mops_entity      ON mk_obs_payload_store (entity_type, entity_id, created_at);
CREATE INDEX idx_mops_tenant_time ON mk_obs_payload_store (tenant_id, created_at);

ALTER TABLE canonical_resource ADD trace_id VARCHAR2(128) NULL;
CREATE INDEX idx_canonical_resource_trace ON canonical_resource (trace_id);

COMMENT ON TABLE mk_obs_state_transition IS '可观测状态流转表：记录所有引擎实体状态机 from/to、原因、操作者和 traceId';
COMMENT ON COLUMN mk_obs_state_transition.entity_type IS '引擎实体类型，例如 clinical_event、recommendation_trigger';
COMMENT ON COLUMN mk_obs_state_transition.entity_id IS '引擎实体业务 ID';
COMMENT ON COLUMN mk_obs_state_transition.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_obs_state_transition.org_path IS '组织路径快照';
COMMENT ON COLUMN mk_obs_state_transition.trace_id IS '请求或异步任务 traceId';
COMMENT ON COLUMN mk_obs_state_transition.error_code IS '失败时的统一错误码';
COMMENT ON COLUMN mk_obs_state_transition.occurred_at IS '状态流转发生时间';
COMMENT ON COLUMN mk_obs_state_transition.created_by IS '记录创建操作者';
COMMENT ON TABLE mk_obs_payload_store IS '可观测 payload 存储表：统一保存引擎输入输出大报文的摘要、位置和软删除状态';
COMMENT ON COLUMN mk_obs_payload_store.payload_id IS 'payload 业务 ID';
COMMENT ON COLUMN mk_obs_payload_store.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_obs_payload_store.org_path IS '组织路径快照';
COMMENT ON COLUMN mk_obs_payload_store.entity_type IS '关联引擎实体类型';
COMMENT ON COLUMN mk_obs_payload_store.entity_id IS '关联引擎实体业务 ID';
COMMENT ON COLUMN mk_obs_payload_store.trace_id IS '请求或异步任务 traceId';
COMMENT ON COLUMN mk_obs_payload_store.storage_type IS '存储类型：INLINE 表内 Base64 / URI 外部对象存储';
COMMENT ON COLUMN mk_obs_payload_store.content_type IS 'payload 内容类型';
COMMENT ON COLUMN mk_obs_payload_store.digest IS 'payload SHA-256 摘要';
COMMENT ON COLUMN mk_obs_payload_store.payload_base64 IS 'INLINE 存储时的 Base64 payload';
COMMENT ON COLUMN mk_obs_payload_store.deleted_at IS '软删除时间，非空表示已归档或删除';
