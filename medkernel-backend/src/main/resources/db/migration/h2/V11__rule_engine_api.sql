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

CREATE TABLE IF NOT EXISTS rule_applicability (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_version_id   VARCHAR(64)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    population_json   CLOB         NOT NULL,
    org_scope_json    CLOB         NOT NULL,
    settings_json     CLOB         NOT NULL,
    effective_from    DATE         NULL,
    effective_to      DATE         NULL,
    rollout_percent   INT          NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    trace_id          VARCHAR(128) NULL,
    CONSTRAINT uk_rule_applicability_version UNIQUE (tenant_id, rule_version_id),
    CONSTRAINT ck_rule_applicability_rollout CHECK (rollout_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_rule_applicability_dates CHECK (
        effective_from IS NULL OR effective_to IS NULL OR effective_from <= effective_to
    )
);

CREATE INDEX IF NOT EXISTS idx_rule_applicability_effective
    ON rule_applicability (tenant_id, effective_from, effective_to);

CREATE TABLE IF NOT EXISTS rule_governance (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    governance_id      VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    rule_version_id    VARCHAR(64)  NOT NULL,
    state              VARCHAR(32)  NOT NULL,
    required_signoffs  INT          NOT NULL,
    review_round       INT          NOT NULL,
    author_id          VARCHAR(64)  NOT NULL,
    last_reason        VARCHAR(500) NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(64)  NOT NULL,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(64)  NOT NULL,
    trace_id           VARCHAR(128) NULL,
    lock_version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_rule_governance_id UNIQUE (governance_id),
    CONSTRAINT uk_rule_governance_version UNIQUE (tenant_id, rule_version_id),
    CONSTRAINT ck_rule_governance_state CHECK (
        state IN ('DRAFT','PEER_REVIEW','COMMITTEE','SHADOW','CANARY','FULL','MONITOR','RETIRED')
    ),
    CONSTRAINT ck_rule_governance_signoffs CHECK (required_signoffs BETWEEN 1 AND 2),
    CONSTRAINT ck_rule_governance_round CHECK (review_round >= 1)
);

CREATE INDEX IF NOT EXISTS idx_rule_governance_state
    ON rule_governance (tenant_id, state, updated_at);

CREATE TABLE IF NOT EXISTS rule_signoff (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    signoff_id         VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL,
    rule_version_id    VARCHAR(64)  NOT NULL,
    stage              VARCHAR(32)  NOT NULL,
    review_round       INT          NOT NULL,
    signer_role        VARCHAR(64)  NOT NULL,
    signer_id          VARCHAR(64)  NOT NULL,
    decision           VARCHAR(20)  NOT NULL,
    reason             VARCHAR(500) NOT NULL,
    signed_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id           VARCHAR(128) NULL,
    CONSTRAINT uk_rule_signoff_id UNIQUE (signoff_id),
    CONSTRAINT uk_rule_signoff_signer UNIQUE (
        tenant_id, rule_version_id, stage, review_round, signer_id
    ),
    CONSTRAINT ck_rule_signoff_stage CHECK (stage IN ('PEER_REVIEW','COMMITTEE')),
    CONSTRAINT ck_rule_signoff_decision CHECK (decision IN ('APPROVED','REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_rule_signoff_version
    ON rule_signoff (tenant_id, rule_version_id, stage, signed_at);

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
    CONSTRAINT ck_rule_execution_status CHECK (status IN ('SUCCESS','SHADOW_RECORDED','MISS','NOT_APPLICABLE','SUPPRESSED','DEDUPLICATED','FAILED')),
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

CREATE TABLE IF NOT EXISTS rule_shadow_feedback (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feedback_id      VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    execution_id     VARCHAR(64)  NOT NULL,
    rule_id          VARCHAR(64)  NOT NULL,
    version_id       VARCHAR(64)  NOT NULL,
    patient_id       VARCHAR(64)  NULL,
    encounter_id     VARCHAR(64)  NULL,
    decision         VARCHAR(32)  NOT NULL,
    reason           VARCHAR(500) NULL,
    assessed_by      VARCHAR(64)  NOT NULL,
    assessed_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id         VARCHAR(128) NULL,
    CONSTRAINT uk_rule_shadow_feedback_id UNIQUE (feedback_id),
    CONSTRAINT uk_rule_shadow_feedback_execution UNIQUE (tenant_id, execution_id),
    CONSTRAINT ck_rule_shadow_feedback_decision CHECK (decision IN ('TRUE_POSITIVE','FALSE_POSITIVE'))
);

CREATE INDEX IF NOT EXISTS idx_rule_shadow_feedback_rule_time
    ON rule_shadow_feedback (tenant_id, rule_id, assessed_at);
CREATE INDEX IF NOT EXISTS idx_rule_shadow_feedback_decision
    ON rule_shadow_feedback (tenant_id, rule_id, decision);

CREATE TABLE IF NOT EXISTS rule_backtest_run (
    id                            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    backtest_id                   VARCHAR(64)  NOT NULL,
    tenant_id                     VARCHAR(64)  NOT NULL,
    rule_id                       VARCHAR(64)  NOT NULL,
    version_id                    VARCHAR(64)  NOT NULL,
    cohort_ref                    VARCHAR(120) NULL,
    sample_count                  INT          NOT NULL,
    true_positive_count           INT          NOT NULL,
    false_positive_count          INT          NOT NULL,
    true_negative_count           INT          NOT NULL,
    false_negative_count          INT          NOT NULL,
    sensitivity                   DOUBLE PRECISION NOT NULL,
    specificity                   DOUBLE PRECISION NOT NULL,
    accuracy                      DOUBLE PRECISION NOT NULL,
    fire_rate                     DOUBLE PRECISION NOT NULL,
    false_positive_examples_json  CLOB         NOT NULL,
    false_negative_examples_json  CLOB         NOT NULL,
    created_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                    VARCHAR(64)  NOT NULL,
    trace_id                      VARCHAR(128) NULL,
    CONSTRAINT uk_rule_backtest_id UNIQUE (backtest_id),
    CONSTRAINT ck_rule_backtest_sample CHECK (sample_count > 0),
    CONSTRAINT ck_rule_backtest_counts CHECK (
        true_positive_count >= 0
        AND false_positive_count >= 0
        AND true_negative_count >= 0
        AND false_negative_count >= 0
    ),
    CONSTRAINT ck_rule_backtest_rates CHECK (
        sensitivity BETWEEN 0 AND 1
        AND specificity BETWEEN 0 AND 1
        AND accuracy BETWEEN 0 AND 1
        AND fire_rate BETWEEN 0 AND 1
    )
);

CREATE INDEX IF NOT EXISTS idx_rule_backtest_rule_time
    ON rule_backtest_run (tenant_id, rule_id, created_at);
CREATE INDEX IF NOT EXISTS idx_rule_backtest_version_time
    ON rule_backtest_run (tenant_id, version_id, created_at);

CREATE TABLE IF NOT EXISTS rule_drift_snapshot (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drift_id              VARCHAR(64)  NOT NULL,
    tenant_id             VARCHAR(64)  NOT NULL,
    rule_id               VARCHAR(64)  NOT NULL,
    version_id            VARCHAR(64)  NOT NULL,
    baseline_backtest_id  VARCHAR(64)  NOT NULL,
    window_start          TIMESTAMP    NOT NULL,
    window_end            TIMESTAMP    NOT NULL,
    sample_count          BIGINT       NOT NULL,
    hit_count             BIGINT       NOT NULL,
    baseline_fire_rate    DOUBLE PRECISION NOT NULL,
    current_fire_rate     DOUBLE PRECISION NOT NULL,
    drift_delta           DOUBLE PRECISION NOT NULL,
    threshold             DOUBLE PRECISION NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(64)  NOT NULL,
    trace_id              VARCHAR(128) NULL,
    CONSTRAINT uk_rule_drift_snapshot_id UNIQUE (drift_id),
    CONSTRAINT ck_rule_drift_window CHECK (window_start < window_end),
    CONSTRAINT ck_rule_drift_sample CHECK (sample_count > 0 AND hit_count BETWEEN 0 AND sample_count),
    CONSTRAINT ck_rule_drift_rates CHECK (
        baseline_fire_rate BETWEEN 0 AND 1
        AND current_fire_rate BETWEEN 0 AND 1
        AND threshold BETWEEN 0 AND 1
        AND drift_delta BETWEEN -1 AND 1
    ),
    CONSTRAINT ck_rule_drift_status CHECK (status IN ('STABLE','WARNING'))
);

CREATE INDEX IF NOT EXISTS idx_rule_drift_rule_time
    ON rule_drift_snapshot (tenant_id, rule_id, created_at);
CREATE INDEX IF NOT EXISTS idx_rule_drift_status
    ON rule_drift_snapshot (tenant_id, rule_id, status);

COMMENT ON COLUMN rule_definition.priority IS '规则优先级，数值越大越先执行';
COMMENT ON COLUMN rule_definition.suppressed_by IS '抑制当前规则的高阶规则编码';
COMMENT ON COLUMN rule_definition.dedupe_window_seconds IS '同患者同语义动作去重窗口秒数，0 表示不去重';
COMMENT ON COLUMN rule_execution_log.patient_id IS '用于交互治理的患者业务 ID，不保存患者详情';
COMMENT ON COLUMN rule_execution_log.encounter_id IS '用于交互治理的就诊业务 ID，不保存就诊详情';
COMMENT ON COLUMN rule_execution_log.semantic_key IS '规则动作语义键，用于同患者窗口去重';
COMMENT ON COLUMN rule_execution_log.deduplicated_from_execution_id IS '命中窗口去重时指向首次执行 ID';
COMMENT ON TABLE rule_applicability IS '规则版本适用域检索镜像，权威内容为规则 DSL 的 applicability';
COMMENT ON COLUMN rule_applicability.population_json IS '人群纳入与排除条件 JSON';
COMMENT ON COLUMN rule_applicability.org_scope_json IS '集团、医院、科室组织范围 JSON';
COMMENT ON COLUMN rule_applicability.settings_json IS '住院、门诊、急诊、随访场景 JSON';
COMMENT ON COLUMN rule_applicability.rollout_percent IS '稳定灰度比例，取值 0 到 100';
COMMENT ON TABLE rule_governance IS '规则版本知识治理事实，记录从草稿到退役的唯一当前阶段';
COMMENT ON COLUMN rule_governance.required_signoffs IS '进入影子阶段前要求的独立委员会签署人数';
COMMENT ON COLUMN rule_governance.review_round IS '当前评审轮次，驳回后递增，禁止复用旧轮次会签';
COMMENT ON COLUMN rule_governance.author_id IS '规则版本作者，用于职责分离门禁';
COMMENT ON COLUMN rule_governance.lock_version IS '治理状态并发更新版本号，防止批准与驳回相互覆盖';
COMMENT ON TABLE rule_signoff IS '规则同行评审与临床委员会会签证据';
COMMENT ON COLUMN rule_signoff.review_round IS '签署所属评审轮次';
COMMENT ON COLUMN rule_signoff.signer_id IS '签署人用户 ID，同阶段同版本只能签署一次';
COMMENT ON TABLE rule_override_log IS '规则越权日志：记录阻断或强提醒动作的人工越权理由';
COMMENT ON COLUMN rule_override_log.override_reason IS '医师选择或填写的越权理由';
COMMENT ON TABLE rule_shadow_feedback IS '规则影子运行复核事实，用于统计真实命中与误报';
COMMENT ON COLUMN rule_shadow_feedback.decision IS '影子复核结论：TRUE_POSITIVE 真实命中 / FALSE_POSITIVE 误报';
COMMENT ON COLUMN rule_shadow_feedback.reason IS '影子复核说明，误报时必填';
COMMENT ON COLUMN rule_shadow_feedback.assessed_by IS '执行影子复核的用户 ID';
COMMENT ON TABLE rule_backtest_run IS '规则历史回测事实，基于真实脱敏金标准样本计算灵敏度与特异度';
COMMENT ON COLUMN rule_backtest_run.cohort_ref IS '回测样本集引用，例如脱敏快照批次或测试用例集合';
COMMENT ON COLUMN rule_backtest_run.false_positive_examples_json IS '误报样本 ID 列表 JSON';
COMMENT ON COLUMN rule_backtest_run.false_negative_examples_json IS '漏报样本 ID 列表 JSON';
COMMENT ON TABLE rule_drift_snapshot IS '规则上线后漂移监测快照，比较生产窗口命中率与回测基线';
COMMENT ON COLUMN rule_drift_snapshot.baseline_backtest_id IS '作为漂移基线的历史回测 ID';
COMMENT ON COLUMN rule_drift_snapshot.drift_delta IS '当前命中率减基线命中率的差值';
COMMENT ON COLUMN rule_drift_snapshot.status IS '漂移状态：STABLE 稳定 / WARNING 告警';
