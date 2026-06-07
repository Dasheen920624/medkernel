-- MedKernel v1.0 GA · GA-ENG-API-05 规则引擎 API（达梦）

CREATE TABLE rule_definition (
    id                      NUMBER(19)    IDENTITY PRIMARY KEY,
    rule_id                 VARCHAR2(64)  NOT NULL,
    tenant_id               VARCHAR2(64)  NOT NULL,
    rule_code               VARCHAR2(128) NOT NULL,
    name                    VARCHAR2(256) NOT NULL,
    rule_type               VARCHAR2(32)  NOT NULL,
    authoring_mode          VARCHAR2(32)  DEFAULT 'DSL' NOT NULL,
    risk_level              VARCHAR2(16)  DEFAULT 'MEDIUM' NOT NULL,
    status                  VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    active_version_id       VARCHAR2(64)  NULL,
    package_version         VARCHAR2(64)  NULL,
    applicable_org_unit_id  VARCHAR2(64)  NULL,
    created_at              TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by              VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at              TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by              VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id                VARCHAR2(128) NULL,
    CONSTRAINT uk_rule_definition_tenant_code UNIQUE (tenant_id, rule_code),
    CONSTRAINT ck_rule_definition_type CHECK (rule_type IN (
        'DIAGNOSIS','ORDER','LAB','REPORT','DISCHARGE','FOLLOWUP',
        'INSURANCE','QUALITY','RECORD','PATHWAY'
    )),
    CONSTRAINT ck_rule_definition_mode CHECK (authoring_mode IN ('TEMPLATE','VISUAL','DSL')),
    CONSTRAINT ck_rule_definition_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_rule_definition_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED'))
);

CREATE INDEX idx_rule_definition_tenant_status ON rule_definition (tenant_id, status, updated_at);
CREATE INDEX idx_rule_definition_type_risk     ON rule_definition (tenant_id, rule_type, risk_level);

CREATE TABLE rule_version (
    id                  NUMBER(19)    IDENTITY PRIMARY KEY,
    version_id          VARCHAR2(64)  NOT NULL,
    tenant_id           VARCHAR2(64)  NOT NULL,
    rule_id             VARCHAR2(64)  NOT NULL,
    version_no          NUMBER(10)    NOT NULL,
    source_ref          VARCHAR2(512) NOT NULL,
    change_summary      VARCHAR2(512) NULL,
    dsl_json            CLOB          NOT NULL,
    explanation_json    CLOB          NULL,
    status              VARCHAR2(32)  DEFAULT 'DRAFT' NOT NULL,
    published_at        TIMESTAMP     NULL,
    published_by        VARCHAR2(64)  NULL,
    rollback_version_id VARCHAR2(64)  NULL,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by          VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id            VARCHAR2(128) NULL,
    CONSTRAINT uk_rule_version_rule_no UNIQUE (tenant_id, rule_id, version_no),
    CONSTRAINT ck_rule_version_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED'))
);

CREATE INDEX idx_rule_version_rule_status ON rule_version (tenant_id, rule_id, status);

CREATE TABLE rule_test_case (
    id                   NUMBER(19)    IDENTITY PRIMARY KEY,
    case_id              VARCHAR2(64)  NOT NULL,
    tenant_id            VARCHAR2(64)  NOT NULL,
    rule_id              VARCHAR2(64)  NOT NULL,
    version_id           VARCHAR2(64)  NOT NULL,
    case_type            VARCHAR2(32)  NOT NULL,
    context_snapshot_id  VARCHAR2(64)  NOT NULL,
    input_payload        CLOB          NOT NULL,
    expected_hit         NUMBER(1)     NOT NULL,
    expected_severity    VARCHAR2(16)  NULL,
    expected_action_code VARCHAR2(64)  NULL,
    last_hit             NUMBER(1)     NULL,
    last_status          VARCHAR2(32)  NULL,
    last_message         VARCHAR2(512) NULL,
    last_run_at          TIMESTAMP     NULL,
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by           VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    trace_id             VARCHAR2(128) NULL,
    CONSTRAINT uk_rule_test_case_id UNIQUE (case_id),
    CONSTRAINT ck_rule_test_case_type CHECK (case_type IN ('POSITIVE','NEGATIVE','BOUNDARY','CONFLICT')),
    CONSTRAINT ck_rule_test_case_status CHECK (last_status IS NULL OR last_status IN ('NOT_RUN','PASS','FAIL','ERROR'))
);

CREATE INDEX idx_rule_test_case_version_type ON rule_test_case (tenant_id, version_id, case_type);

CREATE TABLE rule_execution_log (
    id               NUMBER(19)    IDENTITY PRIMARY KEY,
    execution_id     VARCHAR2(64)  NOT NULL,
    tenant_id        VARCHAR2(64)  NOT NULL,
    rule_id          VARCHAR2(64)  NOT NULL,
    version_id       VARCHAR2(64)  NOT NULL,
    trigger_point    VARCHAR2(64)  NOT NULL,
    event_id         VARCHAR2(64)  NULL,
    actor_user_id    VARCHAR2(64)  NULL,
    input_digest     VARCHAR2(128) NOT NULL,
    hit              NUMBER(1)     NOT NULL,
    severity         VARCHAR2(16)  NULL,
    actions_json     CLOB          NULL,
    explanation_json CLOB          NULL,
    status           VARCHAR2(32)  DEFAULT 'SUCCESS' NOT NULL,
    error_code       VARCHAR2(64)  NULL,
    error_class      VARCHAR2(32)  NULL,
    executed_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    trace_id         VARCHAR2(128) NULL,
    CONSTRAINT uk_rule_execution_id UNIQUE (execution_id),
    CONSTRAINT ck_rule_execution_status CHECK (status IN ('SUCCESS','MISS','FAILED')),
    CONSTRAINT ck_rule_execution_severity CHECK (severity IS NULL OR severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

CREATE INDEX idx_rule_execution_tenant_time ON rule_execution_log (tenant_id, executed_at);
CREATE INDEX idx_rule_execution_rule_time   ON rule_execution_log (tenant_id, rule_id, executed_at);
CREATE INDEX idx_rule_execution_trigger     ON rule_execution_log (tenant_id, trigger_point, executed_at);
