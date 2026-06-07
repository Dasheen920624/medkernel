-- MedKernel v1.0 GA · GA-ENG-API-02 临床事件 API（Oracle）
-- ROLLBACK: 回退时先恢复原临床事件状态约束，再删除本迁移新增的事件载荷、出站表与字段。

ALTER TABLE clinical_event ADD patient_id VARCHAR2(64) NULL;
ALTER TABLE clinical_event ADD encounter_id VARCHAR2(64) NULL;
ALTER TABLE clinical_event ADD clinical_setting VARCHAR2(16) NOT NULL;
ALTER TABLE clinical_event ADD package_version VARCHAR2(64) NULL;
ALTER TABLE clinical_event ADD error_code VARCHAR2(64) NULL;
ALTER TABLE clinical_event ADD error_class VARCHAR2(32) NULL;
ALTER TABLE clinical_event ADD retry_count NUMBER(10) DEFAULT 0 NOT NULL;
ALTER TABLE clinical_event ADD root_event_id VARCHAR2(64) NULL;

ALTER TABLE clinical_event DROP CONSTRAINT ck_clinical_event_status;
ALTER TABLE clinical_event ADD CONSTRAINT ck_clinical_event_status
    CHECK (processing_status IN ('RECEIVED','MAPPED','PROCESSED','FAILED','SUPERSEDED'));
ALTER TABLE clinical_event ADD CONSTRAINT ck_clinical_event_setting
    CHECK (clinical_setting IN ('INPATIENT','OUTPATIENT','ED','FOLLOWUP'));

CREATE INDEX idx_clinical_event_patient   ON clinical_event (tenant_id, patient_id, received_at);
CREATE INDEX idx_clinical_event_encounter ON clinical_event (tenant_id, encounter_id, received_at);
COMMENT ON COLUMN clinical_event.clinical_setting IS '标准临床场景：住院、门诊、急诊或随访';

CREATE TABLE clinical_event_payload (
    id              NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        VARCHAR2(64)  NOT NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    payload         CLOB          NULL,
    payload_uri     VARCHAR2(256) NULL,
    storage_type    VARCHAR2(16)  DEFAULT 'INLINE' NOT NULL,
    content_type    VARCHAR2(64)  DEFAULT 'application/json' NOT NULL,
    digest          VARCHAR2(128) NOT NULL,
    size_bytes      NUMBER(19)    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    deleted_at      TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_event_payload UNIQUE (event_id),
    CONSTRAINT ck_storage_type CHECK (storage_type IN ('INLINE','URI'))
);

CREATE INDEX idx_cep_tenant_time ON clinical_event_payload (tenant_id, created_at);
COMMENT ON TABLE clinical_event_payload IS '临床事件原始载荷与摘要';

CREATE TABLE clinical_event_outbox (
    id              NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        VARCHAR2(64)  NOT NULL,
    tenant_id       VARCHAR2(64)  NOT NULL,
    trace_id        VARCHAR2(128) NULL,
    actor_user_id   VARCHAR2(64)  NULL,
    claim_status    VARCHAR2(16)  DEFAULT 'PENDING' NOT NULL,
    claimed_by      VARCHAR2(64)  NULL,
    claimed_at      TIMESTAMP WITH TIME ZONE NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    retry_count     NUMBER(10)    DEFAULT 0 NOT NULL,
    last_error_code VARCHAR2(64)  NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    processed_at    TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_status CHECK (claim_status IN ('PENDING','CLAIMED','PROCESSED','DEAD'))
);

CREATE INDEX idx_outbox_pending ON clinical_event_outbox (claim_status, next_attempt_at);
CREATE INDEX idx_outbox_tenant  ON clinical_event_outbox (tenant_id, created_at);
COMMENT ON TABLE clinical_event_outbox IS '临床事件可靠处理出站队列';
