CREATE TABLE evaluation_indicator (
    id NUMBER(19) IDENTITY PRIMARY KEY,
    indicator_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    indicator_code VARCHAR2(64) NOT NULL,
    version_no INT NOT NULL,
    name VARCHAR2(256) NOT NULL,
    subject_type VARCHAR2(32) NOT NULL,
    denominator_definition CLOB NOT NULL,
    numerator_definition CLOB NOT NULL,
    exclusion_definition CLOB,
    scoring_definition CLOB,
    time_window VARCHAR2(128) NOT NULL,
    organization_scope CLOB NOT NULL,
    responsible_department_id VARCHAR2(64),
    source_ref VARCHAR2(256),
    package_version VARCHAR2(64),
    status VARCHAR2(32) DEFAULT 'DRAFT' NOT NULL,
    published_at TIMESTAMP,
    published_by VARCHAR2(64),
    activated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(64),
    CONSTRAINT uk_eval_indicator_id UNIQUE (tenant_id, indicator_id),
    CONSTRAINT uk_eval_indicator_tenant_version UNIQUE (tenant_id, indicator_code, version_no),
    CONSTRAINT ck_eval_indicator_subject CHECK (subject_type IN ('PATIENT', 'MEDICAL_RECORD', 'DEPARTMENT', 'DOCTOR', 'DISEASE', 'PATHWAY', 'CLAIM', 'FOLLOWUP')),
    CONSTRAINT ck_eval_indicator_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'GRAY', 'ACTIVE', 'OFFLINE', 'ARCHIVED'))
);

CREATE INDEX idx_eval_indicator_tenant_status ON evaluation_indicator (tenant_id, status, updated_at);
CREATE INDEX idx_eval_indicator_code_status ON evaluation_indicator (tenant_id, indicator_code, status);

CREATE TABLE evaluation_run (
    id NUMBER(19) IDENTITY PRIMARY KEY,
    run_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    run_code VARCHAR2(64) NOT NULL,
    run_type VARCHAR2(32) NOT NULL,
    source_event_id VARCHAR2(64),
    context_snapshot_id VARCHAR2(64),
    patient_id VARCHAR2(64),
    encounter_id VARCHAR2(64),
    scenario_code VARCHAR2(64),
    package_version VARCHAR2(64),
    input_digest VARCHAR2(128),
    status VARCHAR2(32) DEFAULT 'RECEIVED' NOT NULL,
    error_code VARCHAR2(64),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(64),
    CONSTRAINT uk_eval_run_id UNIQUE (tenant_id, run_id),
    CONSTRAINT uk_eval_run_tenant_code UNIQUE (tenant_id, run_code),
    CONSTRAINT ck_eval_run_type CHECK (run_type IN ('MANUAL_SAMPLE', 'UPSTREAM_RESULT', 'BATCH_IMPORT')),
    CONSTRAINT ck_eval_run_status CHECK (status IN ('RECEIVED', 'RECORDED', 'FAILED'))
);

CREATE INDEX idx_eval_run_tenant_time ON evaluation_run (tenant_id, occurred_at);
CREATE INDEX idx_eval_run_context ON evaluation_run (tenant_id, encounter_id, scenario_code);

CREATE TABLE evaluation_result (
    id NUMBER(19) IDENTITY PRIMARY KEY,
    result_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    run_id VARCHAR2(64) NOT NULL,
    indicator_id VARCHAR2(64) NOT NULL,
    indicator_code VARCHAR2(64) NOT NULL,
    indicator_version INT NOT NULL,
    subject_type VARCHAR2(32) NOT NULL,
    subject_ref_id VARCHAR2(64) NOT NULL,
    score_value NUMBER(18,4),
    result_level VARCHAR2(32) NOT NULL,
    hit_flag NUMBER(1) DEFAULT 0 NOT NULL,
    evidence_summary VARCHAR2(2048),
    source_ref VARCHAR2(256),
    responsible_department_id VARCHAR2(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(64),
    CONSTRAINT uk_eval_result_id UNIQUE (tenant_id, result_id),
    CONSTRAINT ck_eval_result_subject CHECK (subject_type IN ('PATIENT', 'MEDICAL_RECORD', 'DEPARTMENT', 'DOCTOR', 'DISEASE', 'PATHWAY', 'CLAIM', 'FOLLOWUP')),
    CONSTRAINT ck_eval_result_level CHECK (result_level IN ('PASS', 'ATTENTION', 'NON_COMPLIANT', 'CRITICAL'))
);

CREATE INDEX idx_eval_result_run ON evaluation_result (tenant_id, run_id);
CREATE INDEX idx_eval_result_indicator ON evaluation_result (tenant_id, indicator_code, created_at);

CREATE TABLE quality_finding (
    id NUMBER(19) IDENTITY PRIMARY KEY,
    finding_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    run_id VARCHAR2(64) NOT NULL,
    result_id VARCHAR2(64) NOT NULL,
    indicator_id VARCHAR2(64) NOT NULL,
    finding_code VARCHAR2(64) NOT NULL,
    title VARCHAR2(256) NOT NULL,
    description VARCHAR2(2048) NOT NULL,
    severity VARCHAR2(8) NOT NULL,
    status VARCHAR2(32) DEFAULT 'NEW' NOT NULL,
    evidence_summary VARCHAR2(2048) NOT NULL,
    responsible_department_id VARCHAR2(64),
    due_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(64),
    CONSTRAINT uk_quality_finding_id UNIQUE (tenant_id, finding_id),
    CONSTRAINT uk_quality_finding_result_code UNIQUE (tenant_id, result_id, finding_code),
    CONSTRAINT ck_quality_finding_severity CHECK (severity IN ('P0', 'P1', 'P2', 'P3')),
    CONSTRAINT ck_quality_finding_status CHECK (status IN ('NEW', 'ASSIGNED', 'REMEDIATING', 'CLOSED', 'WAIVED'))
);

CREATE INDEX idx_quality_finding_status ON quality_finding (tenant_id, status, severity);
CREATE INDEX idx_quality_finding_department ON quality_finding (tenant_id, responsible_department_id, due_at);

CREATE TABLE rectification_task (
    id NUMBER(19) IDENTITY PRIMARY KEY,
    task_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    finding_id VARCHAR2(64) NOT NULL,
    responsible_department_id VARCHAR2(64) NOT NULL,
    assignee_user_id VARCHAR2(64),
    status VARCHAR2(32) DEFAULT 'ASSIGNED' NOT NULL,
    due_at TIMESTAMP NOT NULL,
    rectification_summary VARCHAR2(2048),
    evidence_ref VARCHAR2(256),
    submitted_at TIMESTAMP,
    submitted_by VARCHAR2(64),
    closed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(64),
    CONSTRAINT uk_rect_task_id UNIQUE (tenant_id, task_id),
    CONSTRAINT uk_rect_task_finding UNIQUE (tenant_id, finding_id),
    CONSTRAINT ck_rect_task_status CHECK (status IN ('ASSIGNED', 'SUBMITTED', 'RETURNED', 'CLOSED', 'WAIVED'))
);

CREATE INDEX idx_rect_task_finding ON rectification_task (tenant_id, finding_id, status);
CREATE INDEX idx_rect_task_department_status ON rectification_task (tenant_id, responsible_department_id, status, due_at);

CREATE TABLE rectification_review (
    id NUMBER(19) IDENTITY PRIMARY KEY,
    review_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    finding_id VARCHAR2(64) NOT NULL,
    task_id VARCHAR2(64) NOT NULL,
    decision VARCHAR2(32) NOT NULL,
    review_comment VARCHAR2(2048),
    evidence_ref VARCHAR2(256),
    reviewer_id VARCHAR2(64) NOT NULL,
    reviewed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(64),
    CONSTRAINT uk_rect_review_id UNIQUE (tenant_id, review_id),
    CONSTRAINT ck_rect_review_decision CHECK (decision IN ('APPROVED', 'RETURNED', 'WAIVED'))
);

CREATE INDEX idx_rect_review_finding ON rectification_review (tenant_id, finding_id, reviewed_at);

CREATE TABLE evaluation_idempotency_key (
    id NUMBER(19) IDENTITY PRIMARY KEY,
    tenant_id VARCHAR2(64) NOT NULL,
    idem_key VARCHAR2(128) NOT NULL,
    operation_type VARCHAR2(32) NOT NULL,
    finding_id VARCHAR2(64) NOT NULL,
    task_id VARCHAR2(64) NOT NULL,
    review_id VARCHAR2(64),
    request_digest VARCHAR2(128) NOT NULL,
    finding_status VARCHAR2(32) NOT NULL,
    task_status VARCHAR2(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR2(64) NOT NULL,
    trace_id VARCHAR2(64),
    CONSTRAINT uk_eval_idempotency_operation_key UNIQUE (tenant_id, operation_type, idem_key),
    CONSTRAINT ck_eval_idempotency_operation CHECK (operation_type IN ('RECTIFICATION_SUBMIT', 'RECTIFICATION_REVIEW')),
    CONSTRAINT ck_eval_idempotency_finding_status CHECK (finding_status IN ('NEW', 'ASSIGNED', 'REMEDIATING', 'CLOSED', 'WAIVED')),
    CONSTRAINT ck_eval_idempotency_task_status CHECK (task_status IN ('ASSIGNED', 'SUBMITTED', 'RETURNED', 'CLOSED', 'WAIVED'))
);

CREATE INDEX idx_eval_idempotency_resource
    ON evaluation_idempotency_key (tenant_id, finding_id, operation_type, created_at);
