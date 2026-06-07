-- MedKernel v1.0 GA · GA-ENG-API-06 路径引擎 API（达梦）

CREATE TABLE specialty_package (
    id                NUMBER(19)    IDENTITY PRIMARY KEY,
    package_id        VARCHAR2(64)  NOT NULL,
    tenant_id         VARCHAR2(64)  NOT NULL,
    package_code      VARCHAR2(128) NOT NULL,
    disease_code      VARCHAR2(128) NOT NULL,
    name              VARCHAR2(256) NOT NULL,
    package_version   VARCHAR2(64)  NOT NULL,
    status            VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    source_ref        VARCHAR2(512) NOT NULL,
    description       VARCHAR2(1024) NULL,
    published_at      TIMESTAMP     NULL,
    published_by      VARCHAR2(64)  NULL,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128) NULL,
    CONSTRAINT uk_specialty_package_tenant_code UNIQUE (tenant_id, package_code, package_version),
    CONSTRAINT ck_specialty_package_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED'))
);

CREATE INDEX idx_specialty_package_tenant_status ON specialty_package (tenant_id, status, updated_at);
CREATE INDEX idx_specialty_package_disease       ON specialty_package (tenant_id, disease_code);

CREATE TABLE specialty_profile (
    id                   NUMBER(19)    IDENTITY PRIMARY KEY,
    profile_id           VARCHAR2(64)  NOT NULL,
    tenant_id            VARCHAR2(64)  NOT NULL,
    package_id           VARCHAR2(64)  NOT NULL,
    profile_code         VARCHAR2(128) NOT NULL,
    name                 VARCHAR2(256) NOT NULL,
    stratification_json  CLOB          NULL,
    entry_criteria_json  CLOB          NULL,
    exit_criteria_json   CLOB          NULL,
    followup_plan_json   CLOB          NULL,
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id             VARCHAR2(128) NULL,
    CONSTRAINT uk_specialty_profile_package_code UNIQUE (tenant_id, package_id, profile_code)
);

CREATE INDEX idx_specialty_profile_package ON specialty_profile (tenant_id, package_id);

CREATE TABLE pathway_template (
    id                   NUMBER(19)    IDENTITY PRIMARY KEY,
    template_id          VARCHAR2(64)  NOT NULL,
    tenant_id            VARCHAR2(64)  NOT NULL,
    package_id           VARCHAR2(64)  NOT NULL,
    template_code        VARCHAR2(128) NOT NULL,
    name                 VARCHAR2(256) NOT NULL,
    disease_code         VARCHAR2(128) NOT NULL,
    template_version     NUMBER(10)    DEFAULT 1 NOT NULL,
    template_level       VARCHAR2(32)  DEFAULT 'STANDARD' NOT NULL,
    status               VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    entry_mode           VARCHAR2(32)  DEFAULT 'AUTO_SUGGEST' NOT NULL,
    start_node_code      VARCHAR2(128) NULL,
    source_ref           VARCHAR2(512) NOT NULL,
    description          VARCHAR2(1024) NULL,
    entry_criteria_json  CLOB          NULL,
    exit_criteria_json   CLOB          NULL,
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id             VARCHAR2(128) NULL,
    CONSTRAINT uk_pathway_template_tenant_code UNIQUE (tenant_id, template_code, template_version),
    CONSTRAINT ck_pathway_template_level CHECK (template_level IN (
        'STANDARD','GROUP','HOSPITAL','DEPARTMENT','SPECIALTY'
    )),
    CONSTRAINT ck_pathway_template_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED')),
    CONSTRAINT ck_pathway_entry_mode CHECK (entry_mode IN ('AUTO_SUGGEST','MANUAL_CONFIRM'))
);

CREATE INDEX idx_pathway_template_tenant_status ON pathway_template (tenant_id, status, updated_at);
CREATE INDEX idx_pathway_template_package       ON pathway_template (tenant_id, package_id);
CREATE INDEX idx_pathway_template_disease       ON pathway_template (tenant_id, disease_code);

CREATE TABLE pathway_milestone (
    id                         NUMBER(19)    IDENTITY PRIMARY KEY,
    milestone_id               VARCHAR2(64)  NOT NULL,
    tenant_id                  VARCHAR2(64)  NOT NULL,
    template_id                VARCHAR2(64)  NOT NULL,
    phase_code                 VARCHAR2(128) NOT NULL,
    phase_name                 VARCHAR2(256) NOT NULL,
    milestone_code             VARCHAR2(128) NOT NULL,
    name                       VARCHAR2(256) NOT NULL,
    day_offset                 NUMBER(10)    NULL,
    expected_offset_minutes    NUMBER(10)    NULL,
    achievement_criteria_json  CLOB          NULL,
    sort_order                 NUMBER(10)    DEFAULT 0 NOT NULL,
    created_at                 TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by                 VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at                 TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by                 VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id                   VARCHAR2(128) NULL,
    CONSTRAINT uk_pathway_milestone_template_code UNIQUE (tenant_id, template_id, milestone_code),
    CONSTRAINT ck_pathway_milestone_day CHECK (day_offset IS NULL OR day_offset >= 0),
    CONSTRAINT ck_pathway_milestone_expected CHECK (expected_offset_minutes IS NULL OR expected_offset_minutes >= 0)
);

CREATE INDEX idx_pathway_milestone_template_order
    ON pathway_milestone (tenant_id, template_id, sort_order);
CREATE INDEX idx_pathway_milestone_phase_day
    ON pathway_milestone (tenant_id, template_id, phase_code, day_offset);

CREATE TABLE pathway_node (
    id                  NUMBER(19)    IDENTITY PRIMARY KEY,
    node_id             VARCHAR2(64)  NOT NULL,
    tenant_id           VARCHAR2(64)  NOT NULL,
    template_id         VARCHAR2(64)  NOT NULL,
    node_code           VARCHAR2(128) NOT NULL,
    name                VARCHAR2(256) NOT NULL,
    node_type           VARCHAR2(32)  NOT NULL,
    milestone_code      VARCHAR2(128) NULL,
    sort_order          NUMBER(10)    DEFAULT 0 NOT NULL,
    responsible_role    VARCHAR2(128) NULL,
    dependency_json     CLOB          NULL,
    time_window_minutes NUMBER(10)    NULL,
    terminal_flag       NUMBER(1)     DEFAULT 0 NOT NULL,
    config_json         CLOB          NULL,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id            VARCHAR2(128) NULL,
    CONSTRAINT uk_pathway_node_template_code UNIQUE (tenant_id, template_id, node_code),
    CONSTRAINT ck_pathway_node_type CHECK (node_type IN (
        'SCREENING','ASSESSMENT','EXAM','LAB','MEDICATION','SURGERY',
        'NURSING','REHAB','DISCHARGE','FOLLOWUP','QUALITY',
        'DECISION','PARALLEL','WAIT_TIMER','SUBPATHWAY','MANUAL_GATE','ORDER_SET'
    )),
    CONSTRAINT ck_pathway_node_terminal CHECK (terminal_flag IN (0, 1))
);

CREATE INDEX idx_pathway_node_template_order ON pathway_node (tenant_id, template_id, sort_order);

CREATE TABLE pathway_edge (
    id             NUMBER(19)    IDENTITY PRIMARY KEY,
    edge_id        VARCHAR2(64)  NOT NULL,
    tenant_id      VARCHAR2(64)  NOT NULL,
    template_id    VARCHAR2(64)  NOT NULL,
    edge_code      VARCHAR2(128) NOT NULL,
    from_node_code VARCHAR2(128) NOT NULL,
    to_node_code   VARCHAR2(128) NOT NULL,
    edge_type      VARCHAR2(32)  DEFAULT 'DEFAULT' NOT NULL,
    condition_json CLOB          NULL,
    priority       NUMBER(10)    DEFAULT 0 NOT NULL,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by     VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by     VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id       VARCHAR2(128) NULL,
    CONSTRAINT uk_pathway_edge_template_code UNIQUE (tenant_id, template_id, edge_code),
    CONSTRAINT ck_pathway_edge_type CHECK (edge_type IN (
        'DEFAULT','CONDITION','RISK_STRATIFICATION','PATIENT_CHOICE',
        'RESOURCE_UNAVAILABLE','PHYSICIAN_DECISION','ROLLBACK','JOIN'
    ))
);

CREATE INDEX idx_pathway_edge_template_from ON pathway_edge (tenant_id, template_id, from_node_code, priority);
CREATE INDEX idx_pathway_edge_template_to   ON pathway_edge (tenant_id, template_id, to_node_code);

CREATE TABLE patient_pathway (
    id                 NUMBER(19)    IDENTITY PRIMARY KEY,
    patient_pathway_id VARCHAR2(64)  NOT NULL,
    tenant_id          VARCHAR2(64)  NOT NULL,
    patient_id         VARCHAR2(128) NOT NULL,
    encounter_id       VARCHAR2(128) NULL,
    template_id        VARCHAR2(64)  NOT NULL,
    current_node_code  VARCHAR2(128) NULL,
    status             VARCHAR2(32)  DEFAULT 'ENTERED' NOT NULL,
    entered_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at       TIMESTAMP     NULL,
    exited_at          TIMESTAMP     NULL,
    exit_reason        VARCHAR2(512) NULL,
    last_event_id      VARCHAR2(64)  NULL,
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id           VARCHAR2(128) NULL,
    CONSTRAINT uk_patient_pathway_id UNIQUE (patient_pathway_id),
    CONSTRAINT ck_patient_pathway_status CHECK (status IN (
        'ENTERED','NODE_EXECUTING','VARIANCE','COMPLETED','EXITED'
    ))
);

CREATE INDEX idx_patient_pathway_patient         ON patient_pathway (tenant_id, patient_id, entered_at);
CREATE INDEX idx_patient_pathway_template_status ON patient_pathway (tenant_id, template_id, status);

CREATE TABLE pathway_variance (
    id                 NUMBER(19)    IDENTITY PRIMARY KEY,
    variance_id        VARCHAR2(64)  NOT NULL,
    tenant_id          VARCHAR2(64)  NOT NULL,
    patient_pathway_id VARCHAR2(64)  NOT NULL,
    node_code          VARCHAR2(128) NOT NULL,
    variance_type      VARCHAR2(32)  NOT NULL,
    reason             VARCHAR2(1024) NOT NULL,
    resolution_action  VARCHAR2(512) NULL,
    continue_node_code VARCHAR2(128) NULL,
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id           VARCHAR2(128) NULL,
    CONSTRAINT uk_pathway_variance_id UNIQUE (variance_id),
    CONSTRAINT ck_pathway_variance_type CHECK (variance_type IN (
        'MEDICAL','PATIENT_REASON','RESOURCE_REASON','DOCTOR_CHOICE','SYSTEM_REASON'
    ))
);

CREATE INDEX idx_pathway_variance_pathway_time ON pathway_variance (tenant_id, patient_pathway_id, created_at);

CREATE TABLE clinical_clock (
    id                 NUMBER(19)    IDENTITY PRIMARY KEY,
    clock_id           VARCHAR2(64)  NOT NULL,
    tenant_id          VARCHAR2(64)  NOT NULL,
    patient_pathway_id VARCHAR2(64)  NOT NULL,
    node_code          VARCHAR2(128) NOT NULL,
    metric_code        VARCHAR2(128) NULL,
    started_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    due_at             TIMESTAMP     NULL,
    completed_at       TIMESTAMP     NULL,
    status             VARCHAR2(32)  DEFAULT 'RUNNING' NOT NULL,
    baseline_event     VARCHAR2(64)  NULL,
    baseline_at        TIMESTAMP     NULL,
    min_due_at         TIMESTAMP     NULL,
    target_due_at      TIMESTAMP     NULL,
    max_due_at         TIMESTAMP     NULL,
    escalation_level   VARCHAR2(32)  DEFAULT 'NONE' NOT NULL,
    escalation_policy_json CLOB      NULL,
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by         VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id           VARCHAR2(128) NULL,
    CONSTRAINT uk_clinical_clock_id UNIQUE (clock_id),
    CONSTRAINT ck_clinical_clock_status CHECK (status IN (
        'RUNNING','COMPLETED','TIMEOUT','MISSING_DATA','VARIANCE'
    )),
    CONSTRAINT ck_clinical_clock_escalation CHECK (escalation_level IN (
        'NONE','REMINDER','REPORT','QUALITY_RECORD'
    ))
);

CREATE INDEX idx_clinical_clock_pathway ON clinical_clock (tenant_id, patient_pathway_id, started_at);
CREATE INDEX idx_clinical_clock_due     ON clinical_clock (tenant_id, status, due_at);

CREATE TABLE specialty_metric_binding (
    id            NUMBER(19)    IDENTITY PRIMARY KEY,
    binding_id    VARCHAR2(64)  NOT NULL,
    tenant_id     VARCHAR2(64)  NOT NULL,
    package_id    VARCHAR2(64)  NOT NULL,
    template_id   VARCHAR2(64)  NOT NULL,
    node_code     VARCHAR2(128) NOT NULL,
    metric_code   VARCHAR2(128) NOT NULL,
    required_flag NUMBER(1)     DEFAULT 0 NOT NULL,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by    VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by    VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id      VARCHAR2(128) NULL,
    CONSTRAINT uk_specialty_metric_binding UNIQUE (tenant_id, package_id, template_id, node_code, metric_code),
    CONSTRAINT ck_specialty_metric_required CHECK (required_flag IN (0, 1))
);

CREATE INDEX idx_specialty_metric_package  ON specialty_metric_binding (tenant_id, package_id, metric_code);
CREATE INDEX idx_specialty_metric_template ON specialty_metric_binding (tenant_id, template_id, node_code);
