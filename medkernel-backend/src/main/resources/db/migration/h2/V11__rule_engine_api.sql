-- MedKernel v1.0 GA · GA-ENG-API-05 规则引擎 API（H2 baseline，MODE=PostgreSQL 兼容）

CREATE TABLE IF NOT EXISTS rule_definition (
    id                      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id                 VARCHAR(64)  NOT NULL,
    tenant_id               VARCHAR(64)  NOT NULL,
    rule_code               VARCHAR(128) NOT NULL,
    name                    VARCHAR(256) NOT NULL,
    rule_type               VARCHAR(32)  NOT NULL,
    authoring_mode          VARCHAR(32)  NOT NULL DEFAULT 'DSL',
    risk_level              VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    priority                INT          NOT NULL DEFAULT 100,
    suppressed_by           VARCHAR(128) NULL,
    dedupe_window_seconds   INT          NOT NULL DEFAULT 0,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    active_version_id       VARCHAR(64)  NULL,
    package_version         VARCHAR(64)  NULL,
    applicable_org_unit_id  VARCHAR(64)  NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id                VARCHAR(128) NULL,
    CONSTRAINT uk_rule_definition_tenant_code UNIQUE (tenant_id, rule_code),
    CONSTRAINT ck_rule_definition_type CHECK (rule_type IN (
        'DIAGNOSIS','ORDER','LAB','REPORT','DISCHARGE','FOLLOWUP',
        'INSURANCE','QUALITY','RECORD','PATHWAY'
    )),
    CONSTRAINT ck_rule_definition_mode CHECK (authoring_mode IN ('TEMPLATE','VISUAL','DSL')),
    CONSTRAINT ck_rule_definition_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_rule_definition_priority CHECK (priority BETWEEN 0 AND 1000),
    CONSTRAINT ck_rule_definition_dedupe CHECK (dedupe_window_seconds BETWEEN 0 AND 86400),
    CONSTRAINT ck_rule_definition_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_rule_definition_tenant_status ON rule_definition (tenant_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_rule_definition_type_risk     ON rule_definition (tenant_id, rule_type, risk_level);
CREATE INDEX IF NOT EXISTS idx_rule_definition_priority      ON rule_definition (tenant_id, status, priority);

CREATE TABLE IF NOT EXISTS rule_version (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id          VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    rule_id             VARCHAR(64)  NOT NULL,
    version_no          INT          NOT NULL,
    source_ref          VARCHAR(512) NOT NULL,
    change_summary      VARCHAR(512) NULL,
    dsl_json            CLOB         NOT NULL,
    explanation_json    CLOB         NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    published_at        TIMESTAMP    NULL,
    published_by        VARCHAR(64)  NULL,
    rollback_version_id VARCHAR(64)  NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id            VARCHAR(128) NULL,
    CONSTRAINT uk_rule_version_rule_no UNIQUE (tenant_id, rule_id, version_no),
    CONSTRAINT ck_rule_version_status CHECK (status IN ('DRAFT','PUBLISHED','OFFLINE','ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_rule_version_rule_status ON rule_version (tenant_id, rule_id, status);

CREATE TABLE IF NOT EXISTS rule_test_case (
    id                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id              VARCHAR(64)  NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    rule_id              VARCHAR(64)  NOT NULL,
    version_id           VARCHAR(64)  NOT NULL,
    case_type            VARCHAR(32)  NOT NULL,
    context_snapshot_id  VARCHAR(64)  NOT NULL,
    input_payload        CLOB         NOT NULL,
    expected_hit         BOOLEAN      NOT NULL,
    expected_severity    VARCHAR(16)  NULL,
    expected_action_code VARCHAR(64)  NULL,
    last_hit             BOOLEAN      NULL,
    last_status          VARCHAR(32)  NULL,
    last_message         VARCHAR(512) NULL,
    last_run_at          TIMESTAMP    NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id             VARCHAR(128) NULL,
    CONSTRAINT uk_rule_test_case_id UNIQUE (case_id),
    CONSTRAINT ck_rule_test_case_type CHECK (case_type IN ('POSITIVE','NEGATIVE','BOUNDARY','CONFLICT')),
    CONSTRAINT ck_rule_test_case_status CHECK (last_status IS NULL OR last_status IN ('NOT_RUN','PASS','FAIL','ERROR'))
);

CREATE INDEX IF NOT EXISTS idx_rule_test_case_version_type ON rule_test_case (tenant_id, version_id, case_type);

CREATE TABLE IF NOT EXISTS rule_execution_log (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    execution_id     VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    rule_id          VARCHAR(64)  NOT NULL,
    version_id       VARCHAR(64)  NOT NULL,
    trigger_point    VARCHAR(64)  NOT NULL,
    event_id         VARCHAR(64)  NULL,
    actor_user_id    VARCHAR(64)  NULL,
    patient_id       VARCHAR(64)  NULL,
    encounter_id     VARCHAR(64)  NULL,
    semantic_key     VARCHAR(256) NULL,
    input_digest     VARCHAR(128) NOT NULL,
    hit              BOOLEAN      NOT NULL,
    severity         VARCHAR(16)  NULL,
    actions_json     CLOB         NULL,
    explanation_json CLOB         NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS',
    error_code       VARCHAR(64)  NULL,
    error_class      VARCHAR(32)  NULL,
    deduplicated_from_execution_id VARCHAR(64) NULL,
    executed_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id         VARCHAR(128) NULL,
    CONSTRAINT uk_rule_execution_id UNIQUE (execution_id),
    CONSTRAINT ck_rule_execution_status CHECK (status IN ('SUCCESS','MISS','SUPPRESSED','DEDUPLICATED','FAILED')),
    CONSTRAINT ck_rule_execution_severity CHECK (severity IS NULL OR severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_rule_execution_tenant_time ON rule_execution_log (tenant_id, executed_at);
CREATE INDEX IF NOT EXISTS idx_rule_execution_rule_time   ON rule_execution_log (tenant_id, rule_id, executed_at);
CREATE INDEX IF NOT EXISTS idx_rule_execution_trigger     ON rule_execution_log (tenant_id, trigger_point, executed_at);
CREATE INDEX IF NOT EXISTS idx_rule_execution_dedupe      ON rule_execution_log (tenant_id, patient_id, semantic_key, executed_at);

CREATE TABLE IF NOT EXISTS rule_override_log (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    override_id      VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    execution_id     VARCHAR(64)  NOT NULL,
    rule_id          VARCHAR(64)  NOT NULL,
    version_id       VARCHAR(64)  NOT NULL,
    patient_id       VARCHAR(64)  NULL,
    encounter_id     VARCHAR(64)  NULL,
    action_code      VARCHAR(40)  NOT NULL,
    override_reason  VARCHAR(500) NOT NULL,
    overridden_by    VARCHAR(64)  NOT NULL,
    overridden_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id         VARCHAR(128) NULL,
    CONSTRAINT uk_rule_override_id UNIQUE (override_id),
    CONSTRAINT uk_rule_override_execution_action UNIQUE (tenant_id, execution_id, action_code),
    CONSTRAINT ck_rule_override_action CHECK (action_code IN ('BLOCK','STRONG_REMINDER'))
);

CREATE INDEX IF NOT EXISTS idx_rule_override_rule_time ON rule_override_log (tenant_id, rule_id, overridden_at);
CREATE INDEX IF NOT EXISTS idx_rule_override_execution ON rule_override_log (tenant_id, execution_id);

COMMENT ON COLUMN rule_definition.priority IS '规则优先级，数值越大越先执行';
COMMENT ON COLUMN rule_definition.suppressed_by IS '抑制当前规则的高阶规则编码';
COMMENT ON COLUMN rule_definition.dedupe_window_seconds IS '同患者同语义动作去重窗口秒数，0 表示不去重';
COMMENT ON COLUMN rule_execution_log.patient_id IS '用于交互治理的患者业务 ID，不保存患者详情';
COMMENT ON COLUMN rule_execution_log.encounter_id IS '用于交互治理的就诊业务 ID，不保存就诊详情';
COMMENT ON COLUMN rule_execution_log.semantic_key IS '规则动作语义键，用于同患者窗口去重';
COMMENT ON COLUMN rule_execution_log.deduplicated_from_execution_id IS '命中窗口去重时指向首次执行 ID';
COMMENT ON TABLE rule_override_log IS '规则越权日志：记录阻断或强提醒动作的人工越权理由';
COMMENT ON COLUMN rule_override_log.override_reason IS '医师选择或填写的越权理由';
