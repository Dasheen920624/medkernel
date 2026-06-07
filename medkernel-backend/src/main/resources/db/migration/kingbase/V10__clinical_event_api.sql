-- MedKernel v1.0 GA · GA-ENG-API-02 临床事件 API（人大金仓）
-- ROLLBACK: 回退时先恢复原临床事件状态约束，再删除本迁移新增的事件载荷、出站表与字段。

ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS patient_id VARCHAR(64) NULL;
ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS encounter_id VARCHAR(64) NULL;
ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS clinical_setting VARCHAR(16) NOT NULL;
ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS package_version VARCHAR(64) NULL;
ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS error_code VARCHAR(64) NULL;
ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS error_class VARCHAR(32) NULL;
ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS root_event_id VARCHAR(64) NULL;

ALTER TABLE clinical_event DROP CONSTRAINT ck_clinical_event_status;
ALTER TABLE clinical_event ADD CONSTRAINT ck_clinical_event_status
    CHECK (processing_status IN ('RECEIVED','MAPPED','PROCESSED','FAILED','SUPERSEDED'));
ALTER TABLE clinical_event ADD CONSTRAINT ck_clinical_event_setting
    CHECK (clinical_setting IN ('INPATIENT','OUTPATIENT','ED','FOLLOWUP'));

CREATE INDEX IF NOT EXISTS idx_clinical_event_patient   ON clinical_event (tenant_id, patient_id, received_at);
CREATE INDEX IF NOT EXISTS idx_clinical_event_encounter ON clinical_event (tenant_id, encounter_id, received_at);
COMMENT ON COLUMN clinical_event.clinical_setting IS '标准临床场景：住院、门诊、急诊或随访';

CREATE TABLE IF NOT EXISTS clinical_event_payload (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    payload         TEXT         NULL,
    payload_uri     VARCHAR(256) NULL,
    storage_type    VARCHAR(16)  NOT NULL DEFAULT 'INLINE',
    content_type    VARCHAR(64)  NOT NULL DEFAULT 'application/json',
    digest          VARCHAR(128) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uk_event_payload UNIQUE (event_id),
    CONSTRAINT ck_storage_type CHECK (storage_type IN ('INLINE','URI'))
);

CREATE INDEX IF NOT EXISTS idx_cep_tenant_time ON clinical_event_payload (tenant_id, created_at);
COMMENT ON TABLE clinical_event_payload IS '临床事件原始载荷与摘要';

CREATE TABLE IF NOT EXISTS clinical_event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    trace_id        VARCHAR(128) NULL,
    actor_user_id   VARCHAR(64)  NULL,
    claim_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    claimed_by      VARCHAR(64)  NULL,
    claimed_at      TIMESTAMPTZ  NULL,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64)  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ  NULL,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_status CHECK (claim_status IN ('PENDING','CLAIMED','PROCESSED','DEAD'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending ON clinical_event_outbox (claim_status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_outbox_tenant  ON clinical_event_outbox (tenant_id, created_at);
COMMENT ON TABLE clinical_event_outbox IS '临床事件可靠处理出站队列';
