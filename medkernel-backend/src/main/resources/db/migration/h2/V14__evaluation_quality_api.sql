-- MedKernel v1.0 GA · GA-ENG-API-08 评估质控 API（H2 baseline，MODE=PostgreSQL 兼容）

CREATE TABLE IF NOT EXISTS evaluation_indicator (
    id                        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    indicator_id              VARCHAR(64)   NOT NULL,
    tenant_id                 VARCHAR(64)   NOT NULL,
    indicator_code            VARCHAR(128)  NOT NULL,
    version_no                INT           NOT NULL,
    name                      VARCHAR(256)  NOT NULL,
    subject_type              VARCHAR(32)   NOT NULL,
    denominator_definition    CLOB          NOT NULL,
    numerator_definition      CLOB          NOT NULL,
    exclusion_definition      CLOB          NULL,
    scoring_definition        CLOB          NULL,
    time_window               VARCHAR(128)  NOT NULL,
    organization_scope        VARCHAR(256)  NOT NULL,
    responsible_department_id VARCHAR(64)   NOT NULL,
    source_ref                VARCHAR(512)  NOT NULL,
    package_version           VARCHAR(64)   NULL,
    status                    VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    published_at              TIMESTAMP     NULL,
    published_by              VARCHAR(64)   NULL,
    activated_at              TIMESTAMP     NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                  VARCHAR(128)  NULL,
    CONSTRAINT uk_eval_indicator_id UNIQUE (indicator_id),
    CONSTRAINT uk_eval_indicator_tenant_version UNIQUE (tenant_id, indicator_code, version_no),
    CONSTRAINT ck_eval_indicator_subject CHECK (subject_type IN (
        'PATIENT','MEDICAL_RECORD','DEPARTMENT','DOCTOR','DISEASE','PATHWAY','CLAIM','FOLLOWUP'
    )),
    CONSTRAINT ck_eval_indicator_status CHECK (status IN (
        'DRAFT','PENDING_REVIEW','PUBLISHED','GRAY','ACTIVE','OFFLINE','ARCHIVED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_eval_indicator_tenant_status ON evaluation_indicator (tenant_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_eval_indicator_code_status   ON evaluation_indicator (tenant_id, indicator_code, status);

CREATE TABLE IF NOT EXISTS evaluation_run (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id              VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    run_code            VARCHAR(128) NOT NULL,
    run_type            VARCHAR(32)  NOT NULL,
    source_event_id     VARCHAR(64)  NULL,
    context_snapshot_id VARCHAR(64)  NULL,
    patient_id          VARCHAR(128) NULL,
    encounter_id        VARCHAR(128) NULL,
    scenario_code       VARCHAR(128) NOT NULL,
    package_version     VARCHAR(64)  NULL,
    input_digest        VARCHAR(128) NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'RECEIVED',
    error_code          VARCHAR(64)  NULL,
    occurred_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id            VARCHAR(128) NULL,
    CONSTRAINT uk_eval_run_id UNIQUE (run_id),
    CONSTRAINT uk_eval_run_tenant_code UNIQUE (tenant_id, run_code),
    CONSTRAINT ck_eval_run_type CHECK (run_type IN ('MANUAL_SAMPLE','UPSTREAM_RESULT','BATCH_IMPORT')),
    CONSTRAINT ck_eval_run_status CHECK (status IN ('RECEIVED','RECORDED','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_eval_run_tenant_time ON evaluation_run (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_eval_run_context     ON evaluation_run (tenant_id, context_snapshot_id, patient_id, created_at);

CREATE TABLE IF NOT EXISTS evaluation_result (
    id                        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    result_id                 VARCHAR(64)   NOT NULL,
    tenant_id                 VARCHAR(64)   NOT NULL,
    run_id                    VARCHAR(64)   NOT NULL,
    indicator_id              VARCHAR(64)   NOT NULL,
    indicator_code            VARCHAR(128)  NOT NULL,
    indicator_version         INT           NOT NULL,
    subject_type              VARCHAR(32)   NOT NULL,
    subject_ref_id            VARCHAR(128)  NOT NULL,
    score_value               DECIMAL(18,4) NULL,
    result_level              VARCHAR(32)   NOT NULL,
    hit_flag                  BOOLEAN       NOT NULL DEFAULT FALSE,
    evidence_summary          VARCHAR(2048) NOT NULL,
    source_ref                VARCHAR(512)  NULL,
    responsible_department_id VARCHAR(64)   NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                  VARCHAR(128)  NULL,
    CONSTRAINT uk_eval_result_id UNIQUE (result_id),
    CONSTRAINT ck_eval_result_subject CHECK (subject_type IN (
        'PATIENT','MEDICAL_RECORD','DEPARTMENT','DOCTOR','DISEASE','PATHWAY','CLAIM','FOLLOWUP'
    )),
    CONSTRAINT ck_eval_result_level CHECK (result_level IN (
        'PASS','ATTENTION','NON_COMPLIANT','CRITICAL'
    ))
);

CREATE INDEX IF NOT EXISTS idx_eval_result_run       ON evaluation_result (tenant_id, run_id);
CREATE INDEX IF NOT EXISTS idx_eval_result_indicator ON evaluation_result (tenant_id, indicator_id, created_at);

CREATE TABLE IF NOT EXISTS quality_finding (
    id                        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    finding_id                VARCHAR(64)   NOT NULL,
    tenant_id                 VARCHAR(64)   NOT NULL,
    run_id                    VARCHAR(64)   NOT NULL,
    result_id                 VARCHAR(64)   NOT NULL,
    indicator_id              VARCHAR(64)   NOT NULL,
    finding_code              VARCHAR(128)  NOT NULL,
    title                     VARCHAR(256)  NOT NULL,
    description               VARCHAR(2048) NOT NULL,
    severity                  VARCHAR(16)   NOT NULL,
    status                    VARCHAR(32)   NOT NULL DEFAULT 'NEW',
    evidence_summary          VARCHAR(2048) NOT NULL,
    responsible_department_id VARCHAR(64)   NULL,
    due_at                    TIMESTAMP     NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                  VARCHAR(128)  NULL,
    CONSTRAINT uk_quality_finding_id UNIQUE (finding_id),
    CONSTRAINT uk_quality_finding_result_code UNIQUE (tenant_id, result_id, finding_code),
    CONSTRAINT ck_quality_finding_severity CHECK (severity IN ('P0','P1','P2','P3')),
    CONSTRAINT ck_quality_finding_status CHECK (status IN ('NEW','ASSIGNED','REMEDIATING','CLOSED','WAIVED'))
);

CREATE INDEX IF NOT EXISTS idx_quality_finding_status     ON quality_finding (tenant_id, status, severity, created_at);
CREATE INDEX IF NOT EXISTS idx_quality_finding_department ON quality_finding (tenant_id, responsible_department_id, status, due_at);

CREATE TABLE IF NOT EXISTS rectification_task (
    id                        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id                   VARCHAR(64)   NOT NULL,
    tenant_id                 VARCHAR(64)   NOT NULL,
    finding_id                VARCHAR(64)   NOT NULL,
    responsible_department_id VARCHAR(64)   NOT NULL,
    assignee_user_id          VARCHAR(64)   NULL,
    status                    VARCHAR(32)   NOT NULL DEFAULT 'ASSIGNED',
    due_at                    TIMESTAMP     NOT NULL,
    rectification_summary     VARCHAR(2048) NULL,
    evidence_ref              VARCHAR(512)  NULL,
    submitted_at              TIMESTAMP     NULL,
    submitted_by              VARCHAR(64)   NULL,
    closed_at                 TIMESTAMP     NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                  VARCHAR(128)  NULL,
    CONSTRAINT uk_rect_task_id UNIQUE (task_id),
    CONSTRAINT uk_rect_task_finding UNIQUE (tenant_id, finding_id),
    CONSTRAINT ck_rect_task_status CHECK (status IN ('ASSIGNED','SUBMITTED','RETURNED','CLOSED','WAIVED'))
);

CREATE INDEX IF NOT EXISTS idx_rect_task_finding           ON rectification_task (tenant_id, finding_id, status);
CREATE INDEX IF NOT EXISTS idx_rect_task_department_status ON rectification_task (tenant_id, responsible_department_id, status, due_at);

CREATE TABLE IF NOT EXISTS rectification_review (
    id          BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id   VARCHAR(64)   NOT NULL,
    tenant_id   VARCHAR(64)   NOT NULL,
    finding_id  VARCHAR(64)   NOT NULL,
    task_id     VARCHAR(64)   NOT NULL,
    decision    VARCHAR(32)   NOT NULL,
    review_comment VARCHAR(2048) NULL,
    evidence_ref VARCHAR(512) NULL,
    reviewer_id VARCHAR(64)   NOT NULL,
    reviewed_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id    VARCHAR(128)  NULL,
    CONSTRAINT uk_rect_review_id UNIQUE (review_id),
    CONSTRAINT ck_rect_review_decision CHECK (decision IN ('APPROVED','RETURNED','WAIVED'))
);

CREATE INDEX IF NOT EXISTS idx_rect_review_finding ON rectification_review (tenant_id, finding_id, reviewed_at);

CREATE TABLE IF NOT EXISTS evaluation_idempotency_key (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(64)  NOT NULL,
    idem_key       VARCHAR(128) NOT NULL,
    operation_type VARCHAR(32)  NOT NULL,
    finding_id     VARCHAR(64)  NOT NULL,
    task_id        VARCHAR(64)  NOT NULL,
    review_id      VARCHAR(64)  NULL,
    request_digest VARCHAR(128) NOT NULL,
    finding_status VARCHAR(32)  NOT NULL,
    task_status    VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id       VARCHAR(128) NULL,
    CONSTRAINT uk_eval_idempotency_operation_key UNIQUE (tenant_id, operation_type, idem_key),
    CONSTRAINT ck_eval_idempotency_operation CHECK (operation_type IN ('RECTIFICATION_SUBMIT','RECTIFICATION_REVIEW')),
    CONSTRAINT ck_eval_idempotency_finding_status CHECK (finding_status IN ('NEW','ASSIGNED','REMEDIATING','CLOSED','WAIVED')),
    CONSTRAINT ck_eval_idempotency_task_status CHECK (task_status IN ('ASSIGNED','SUBMITTED','RETURNED','CLOSED','WAIVED'))
);

CREATE INDEX IF NOT EXISTS idx_eval_idempotency_resource
    ON evaluation_idempotency_key (tenant_id, finding_id, operation_type, created_at);
