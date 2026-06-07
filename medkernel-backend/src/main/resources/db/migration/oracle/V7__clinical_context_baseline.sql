-- MedKernel v1.0 GA · GA-ENG-API-01 标准临床上下文表族（Oracle 23ai）
-- snapshot 一经创建即不可变；唯一权威生效，包版本快照保证可重放。

CREATE TABLE context_snapshot (
    id                          NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    snapshot_id                 VARCHAR2(64)  NOT NULL,
    tenant_id                   VARCHAR2(64)  NOT NULL,
    org_unit_id                 VARCHAR2(64)  NOT NULL,
    patient_id                  VARCHAR2(64)  NOT NULL,
    encounter_id                VARCHAR2(64)  NULL,
    package_version             VARCHAR2(64)  NOT NULL,
    status                      VARCHAR2(32)  DEFAULT 'ACTIVE' NOT NULL,
    missing_fields              CLOB          NULL,
    mapping_status              CLOB          NULL,
    quality_status              VARCHAR2(32)  DEFAULT 'VALID' NOT NULL,
    trace_id                    VARCHAR2(128) NULL,
    signature                   VARCHAR2(512) NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by                  VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_context_snapshot_id UNIQUE (snapshot_id),
    CONSTRAINT ck_context_snapshot_status CHECK (status IN ('DRAFT','ACTIVE','SUPERSEDED','REJECTED')),
    CONSTRAINT ck_context_snapshot_quality CHECK (quality_status IN ('VALID','PARTIAL','INVALID'))
);

CREATE INDEX idx_context_snapshot_tenant_patient ON context_snapshot (tenant_id, patient_id, created_at);
CREATE INDEX idx_context_snapshot_tenant_enc     ON context_snapshot (tenant_id, encounter_id);
CREATE INDEX idx_context_snapshot_status         ON context_snapshot (tenant_id, status, created_at);

COMMENT ON TABLE context_snapshot IS '标准临床上下文 snapshot：一经创建不可变，承担 E2 后续 API 的数据入口';

CREATE TABLE canonical_resource (
    id                  NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_id         VARCHAR2(64)  NOT NULL,
    snapshot_id         VARCHAR2(64)  NOT NULL,
    tenant_id           VARCHAR2(64)  NOT NULL,
    resource_type       VARCHAR2(32)  NOT NULL,
    resource_payload    CLOB          NOT NULL,
    source_system       VARCHAR2(64)  NULL,
    source_record_id    VARCHAR2(128) NULL,
    mapped_version      VARCHAR2(64)  NULL,
    event_time          TIMESTAMP WITH TIME ZONE NULL,
    received_time       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    quality_status      VARCHAR2(32)  DEFAULT 'VALID' NOT NULL,
    seq_no              NUMBER(10)    DEFAULT 0 NOT NULL,
    CONSTRAINT uk_canonical_resource_id UNIQUE (resource_id),
    CONSTRAINT ck_canonical_resource_type CHECK (resource_type IN (
        'PATIENT','ENCOUNTER','CONDITION','SYMPTOM','OBSERVATION',
        'DIAGNOSTIC_REPORT','MEDICATION','PROCEDURE','DOCUMENT',
        'CARE_PLAN','FOLLOW_UP','CLAIM'
    )),
    CONSTRAINT ck_canonical_resource_quality CHECK (quality_status IN ('VALID','PARTIAL','INVALID'))
);

CREATE INDEX idx_canonical_resource_snapshot    ON canonical_resource (snapshot_id, resource_type, seq_no);
CREATE INDEX idx_canonical_resource_tenant_type ON canonical_resource (tenant_id, resource_type);
COMMENT ON TABLE canonical_resource IS '上下文快照内的标准临床资源单元';

CREATE TABLE clinical_event (
    id                  NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id            VARCHAR2(64)  NOT NULL,
    tenant_id           VARCHAR2(64)  NOT NULL,
    event_type          VARCHAR2(32)  NOT NULL,
    source_system       VARCHAR2(64)  NULL,
    payload_digest      VARCHAR2(128) NULL,
    occurred_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    snapshot_id         VARCHAR2(64)  NULL,
    processing_status   VARCHAR2(32)  DEFAULT 'RECEIVED' NOT NULL,
    trace_id            VARCHAR2(128) NULL,
    CONSTRAINT uk_clinical_event_id UNIQUE (event_id),
    CONSTRAINT ck_clinical_event_type CHECK (event_type IN ('DIAGNOSIS','ORDER','REPORT','DISCHARGE','FOLLOWUP','ADMISSION')),
    CONSTRAINT ck_clinical_event_status CHECK (processing_status IN ('RECEIVED','MAPPED','PROCESSED','FAILED'))
);

CREATE INDEX idx_clinical_event_tenant_received ON clinical_event (tenant_id, received_at);
CREATE INDEX idx_clinical_event_snapshot        ON clinical_event (snapshot_id);
COMMENT ON TABLE clinical_event IS '触发上下文快照创建的临床事件来源';

CREATE TABLE context_idempotency_key (
    id              NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       VARCHAR2(64)  NOT NULL,
    idem_key        VARCHAR2(128) NOT NULL,
    snapshot_id     VARCHAR2(64)  NOT NULL,
    payload_digest  VARCHAR2(128) NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_context_idempotency_tenant_key UNIQUE (tenant_id, idem_key)
);

CREATE INDEX idx_context_idempotency_expires ON context_idempotency_key (expires_at);
COMMENT ON TABLE context_idempotency_key IS '上下文快照请求的幂等键存储';
