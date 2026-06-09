-- MedKernel v1.0 GA · GA-ENG-API-06 路径引擎 API（H2 baseline，MODE=PostgreSQL 兼容）

CREATE TABLE IF NOT EXISTS specialty_profile (
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_id           VARCHAR(64)  NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    package_id           VARCHAR(64)  NOT NULL,
    profile_code         VARCHAR(128) NOT NULL,
    name                 VARCHAR(256) NOT NULL,
    stratification_json  CLOB         NULL,
    entry_criteria_json  CLOB         NULL,
    exit_criteria_json   CLOB         NULL,
    followup_plan_json   CLOB         NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id             VARCHAR(128) NULL,
    CONSTRAINT uk_specialty_profile_package_code UNIQUE (tenant_id, package_id, profile_code)
);

CREATE INDEX IF NOT EXISTS idx_specialty_profile_package ON specialty_profile (tenant_id, package_id);

CREATE TABLE IF NOT EXISTS pathway_template (
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id          VARCHAR(64)  NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    package_id           VARCHAR(64)  NOT NULL,
    template_code        VARCHAR(128) NOT NULL,
    name                 VARCHAR(256) NOT NULL,
    disease_code         VARCHAR(128) NOT NULL,
    template_version     INT          NOT NULL DEFAULT 1,
    template_level       VARCHAR(32)  NOT NULL DEFAULT 'STANDARD',
    parent_template_id   VARCHAR(64)  NULL,
    status               VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    entry_mode           VARCHAR(32)  NOT NULL DEFAULT 'AUTO_SUGGEST',
    start_node_code      VARCHAR(128) NULL,
    source_ref           VARCHAR(512) NOT NULL,
    description          VARCHAR(1024) NULL,
    entry_criteria_json  CLOB         NULL,
    exit_criteria_json   CLOB         NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id             VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_template_tenant_code UNIQUE (tenant_id, template_code, template_version),
    CONSTRAINT ck_pathway_template_level CHECK (template_level IN (
        'STANDARD','GROUP','HOSPITAL','DEPARTMENT','SPECIALTY'
    )),
    CONSTRAINT ck_pathway_template_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED')),
    CONSTRAINT ck_pathway_entry_mode CHECK (entry_mode IN ('AUTO_SUGGEST','MANUAL_CONFIRM'))
);

CREATE INDEX IF NOT EXISTS idx_pathway_template_tenant_status ON pathway_template (tenant_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_pathway_template_package       ON pathway_template (tenant_id, package_id);
CREATE INDEX IF NOT EXISTS idx_pathway_template_disease       ON pathway_template (tenant_id, disease_code);
CREATE INDEX IF NOT EXISTS idx_pathway_template_parent        ON pathway_template (tenant_id, parent_template_id);

CREATE TABLE IF NOT EXISTS pathway_milestone (
    id                         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    milestone_id               VARCHAR(64)  NOT NULL,
    tenant_id                  VARCHAR(64)  NOT NULL,
    template_id                VARCHAR(64)  NOT NULL,
    phase_code                 VARCHAR(128) NOT NULL,
    phase_name                 VARCHAR(256) NOT NULL,
    milestone_code             VARCHAR(128) NOT NULL,
    name                       VARCHAR(256) NOT NULL,
    day_offset                 INT          NULL,
    expected_offset_minutes    INT          NULL,
    achievement_criteria_json  CLOB         NULL,
    sort_order                 INT          NOT NULL DEFAULT 0,
    created_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id                   VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_milestone_template_code UNIQUE (tenant_id, template_id, milestone_code),
    CONSTRAINT ck_pathway_milestone_day CHECK (day_offset IS NULL OR day_offset >= 0),
    CONSTRAINT ck_pathway_milestone_expected CHECK (expected_offset_minutes IS NULL OR expected_offset_minutes >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pathway_milestone_template_order
    ON pathway_milestone (tenant_id, template_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_pathway_milestone_phase_day
    ON pathway_milestone (tenant_id, template_id, phase_code, day_offset);

CREATE TABLE IF NOT EXISTS pathway_node (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    node_id             VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    template_id         VARCHAR(64)  NOT NULL,
    node_code           VARCHAR(128) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    node_type           VARCHAR(32)  NOT NULL,
    milestone_code      VARCHAR(128) NULL,
    sort_order          INT          NOT NULL DEFAULT 0,
    responsible_role    VARCHAR(128) NULL,
    accountable_role    VARCHAR(128) NULL,
    consulted_roles_json CLOB        NULL,
    informed_roles_json CLOB         NULL,
    dependency_json     CLOB         NULL,
    time_window_minutes INT          NULL,
    terminal_flag       BOOLEAN      NOT NULL DEFAULT FALSE,
    disabled_flag       BOOLEAN      NOT NULL DEFAULT FALSE,
    config_json         CLOB         NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id            VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_node_template_code UNIQUE (tenant_id, template_id, node_code),
    CONSTRAINT ck_pathway_node_type CHECK (node_type IN (
        'SCREENING','ASSESSMENT','EXAM','LAB','MEDICATION','SURGERY',
        'NURSING','REHAB','DISCHARGE','FOLLOWUP','QUALITY',
        'DECISION','PARALLEL','WAIT_TIMER','SUBPATHWAY','MANUAL_GATE','ORDER_SET'
    )),
    CONSTRAINT ck_pathway_node_terminal CHECK (terminal_flag IN (TRUE, FALSE)),
    CONSTRAINT ck_pathway_node_disabled CHECK (disabled_flag IN (TRUE, FALSE))
);

CREATE INDEX IF NOT EXISTS idx_pathway_node_template_order ON pathway_node (tenant_id, template_id, sort_order);

CREATE TABLE IF NOT EXISTS pathway_edge (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edge_id        VARCHAR(64)  NOT NULL,
    tenant_id      VARCHAR(64)  NOT NULL,
    template_id    VARCHAR(64)  NOT NULL,
    edge_code      VARCHAR(128) NOT NULL,
    from_node_code VARCHAR(128) NOT NULL,
    to_node_code   VARCHAR(128) NOT NULL,
    edge_type      VARCHAR(32)  NOT NULL DEFAULT 'DEFAULT',
    condition_json CLOB         NULL,
    priority       INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id       VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_edge_template_code UNIQUE (tenant_id, template_id, edge_code),
    CONSTRAINT ck_pathway_edge_type CHECK (edge_type IN (
        'DEFAULT','CONDITION','RISK_STRATIFICATION','PATIENT_CHOICE',
        'RESOURCE_UNAVAILABLE','PHYSICIAN_DECISION','ROLLBACK','JOIN'
    ))
);

CREATE INDEX IF NOT EXISTS idx_pathway_edge_template_from ON pathway_edge (tenant_id, template_id, from_node_code, priority);
CREATE INDEX IF NOT EXISTS idx_pathway_edge_template_to   ON pathway_edge (tenant_id, template_id, to_node_code);

CREATE TABLE IF NOT EXISTS patient_pathway (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_pathway_id VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    patient_id         VARCHAR(128) NOT NULL,
    encounter_id       VARCHAR(128) NULL,
    template_id        VARCHAR(64)  NOT NULL,
    current_node_code  VARCHAR(128) NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'ENTERED',
    entered_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at       TIMESTAMP    NULL,
    exited_at          TIMESTAMP    NULL,
    exit_reason        VARCHAR(512) NULL,
    last_event_id      VARCHAR(64)  NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id           VARCHAR(128) NULL,
    CONSTRAINT uk_patient_pathway_id UNIQUE (patient_pathway_id),
    CONSTRAINT ck_patient_pathway_status CHECK (status IN (
        'ENTERED','NODE_EXECUTING','VARIANCE','COMPLETED','EXITED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_patient_pathway_patient         ON patient_pathway (tenant_id, patient_id, entered_at);
CREATE INDEX IF NOT EXISTS idx_patient_pathway_template_status ON patient_pathway (tenant_id, template_id, status);

CREATE TABLE IF NOT EXISTS pathway_variance (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    variance_id        VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    patient_pathway_id VARCHAR(64)  NOT NULL,
    node_code          VARCHAR(128) NOT NULL,
    variance_type      VARCHAR(32)  NOT NULL,
    reason_code        VARCHAR(128) NOT NULL,
    reason             VARCHAR(1024) NOT NULL,
    responsible_role   VARCHAR(128) NOT NULL,
    resolution_decision VARCHAR(32) NOT NULL,
    resolution_action  VARCHAR(512) NULL,
    continue_node_code VARCHAR(128) NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id           VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_variance_id UNIQUE (variance_id),
    CONSTRAINT ck_pathway_variance_type CHECK (variance_type IN (
        'CLINICAL','SYSTEM','PATIENT','FAMILY'
    )),
    CONSTRAINT ck_pathway_variance_resolution CHECK (resolution_decision IN (
        'HOLD','REENTER','TERMINATE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_pathway_variance_pathway_time ON pathway_variance (tenant_id, patient_pathway_id, created_at);

CREATE TABLE IF NOT EXISTS clinical_clock (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clock_id           VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    patient_pathway_id VARCHAR(64)  NOT NULL,
    node_code          VARCHAR(128) NOT NULL,
    metric_code        VARCHAR(128) NULL,
    started_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at             TIMESTAMP    NULL,
    completed_at       TIMESTAMP    NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    baseline_event     VARCHAR(64)  NULL,
    baseline_at        TIMESTAMP    NULL,
    min_due_at         TIMESTAMP    NULL,
    target_due_at      TIMESTAMP    NULL,
    max_due_at         TIMESTAMP    NULL,
    escalation_level   VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    escalation_policy_json CLOB     NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id           VARCHAR(128) NULL,
    CONSTRAINT uk_clinical_clock_id UNIQUE (clock_id),
    CONSTRAINT ck_clinical_clock_status CHECK (status IN (
        'RUNNING','COMPLETED','TIMEOUT','MISSING_DATA','VARIANCE'
    )),
    CONSTRAINT ck_clinical_clock_escalation CHECK (escalation_level IN (
        'NONE','REMINDER','REPORT','QUALITY_RECORD'
    ))
);

CREATE INDEX IF NOT EXISTS idx_clinical_clock_pathway ON clinical_clock (tenant_id, patient_pathway_id, started_at);
CREATE INDEX IF NOT EXISTS idx_clinical_clock_due     ON clinical_clock (tenant_id, status, due_at);

CREATE TABLE IF NOT EXISTS specialty_metric_binding (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    binding_id    VARCHAR(64)  NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    package_id    VARCHAR(64)  NOT NULL,
    template_id   VARCHAR(64)  NOT NULL,
    node_code     VARCHAR(128) NOT NULL,
    metric_code   VARCHAR(128) NOT NULL,
    required_flag BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id      VARCHAR(128) NULL,
    CONSTRAINT uk_specialty_metric_binding UNIQUE (tenant_id, package_id, template_id, node_code, metric_code),
    CONSTRAINT ck_specialty_metric_required CHECK (required_flag IN (TRUE, FALSE))
);

CREATE INDEX IF NOT EXISTS idx_specialty_metric_package  ON specialty_metric_binding (tenant_id, package_id, metric_code);
CREATE INDEX IF NOT EXISTS idx_specialty_metric_template ON specialty_metric_binding (tenant_id, template_id, node_code);

CREATE TABLE IF NOT EXISTS pathway_outcome_binding (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    binding_id      VARCHAR(64)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    template_id     VARCHAR(64)  NOT NULL,
    scope           VARCHAR(32)  NOT NULL,
    ref_code        VARCHAR(128) NOT NULL,
    indicator_code  VARCHAR(128) NOT NULL,
    package_version VARCHAR(64)  NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id        VARCHAR(128) NULL,
    CONSTRAINT uk_pathway_outcome_binding UNIQUE (tenant_id, template_id, scope, ref_code, indicator_code),
    CONSTRAINT ck_pathway_outcome_scope CHECK (scope IN ('TEMPLATE','PHASE','MILESTONE'))
);

CREATE INDEX IF NOT EXISTS idx_pathway_outcome_template
    ON pathway_outcome_binding (tenant_id, template_id, scope, ref_code);
CREATE INDEX IF NOT EXISTS idx_pathway_outcome_indicator
    ON pathway_outcome_binding (tenant_id, indicator_code);

COMMENT ON TABLE pathway_outcome_binding IS '路径结局指标绑定表，保存路径模板、阶段或里程碑与评估指标的关联';
COMMENT ON COLUMN pathway_outcome_binding.binding_id IS '路径结局指标绑定业务 ID';
COMMENT ON COLUMN pathway_outcome_binding.scope IS '绑定作用域：TEMPLATE 模板、PHASE 阶段、MILESTONE 里程碑';
COMMENT ON COLUMN pathway_outcome_binding.indicator_code IS '评估指标编码，必须对应已激活 EvaluationIndicator';
