-- MedKernel v1.0 GA · OPT-04 临床安全红线静默试运行证据（人大金仓）
-- ROLLBACK: 若需回退，先导出 mk_engine_clinical_redline_trial 试运行证据；已用于红线上线审计的记录不得物理丢失。

CREATE TABLE IF NOT EXISTS mk_engine_clinical_redline_trial (
    id                          BIGSERIAL PRIMARY KEY,
    trial_id                    VARCHAR(64)   NOT NULL,
    tenant_id                   VARCHAR(64)   NOT NULL,
    redline_id                  VARCHAR(64)   NOT NULL,
    redline_key                 VARCHAR(128)  NOT NULL,
    redline_version             VARCHAR(64)   NOT NULL,
    status                      VARCHAR(32)   NOT NULL,
    observed_from               TIMESTAMPTZ   NOT NULL,
    observed_to                 TIMESTAMPTZ   NOT NULL,
    required_silent_hours       BIGINT        NOT NULL,
    actual_silent_hours         BIGINT        NOT NULL,
    evaluated_case_count        BIGINT        NOT NULL,
    matched_case_count          BIGINT        NOT NULL,
    false_positive_case_count   BIGINT        NOT NULL,
    safety_incident_count       BIGINT        NOT NULL,
    gate_passed                 BOOLEAN       NOT NULL DEFAULT FALSE,
    evidence_reference          VARCHAR(512)  NOT NULL,
    operator_note               VARCHAR(1024) NULL,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                    VARCHAR(128)  NULL,
    CONSTRAINT uk_clinical_redline_trial_id UNIQUE (tenant_id, trial_id),
    CONSTRAINT ck_clinical_redline_trial_status CHECK (status IN ('PASSED','FAILED')),
    CONSTRAINT ck_clinical_redline_trial_gate CHECK (gate_passed IN (TRUE, FALSE)),
    CONSTRAINT ck_clinical_redline_trial_counts CHECK (
        required_silent_hours >= 0
        AND actual_silent_hours >= 0
        AND evaluated_case_count >= 0
        AND matched_case_count >= 0
        AND false_positive_case_count >= 0
        AND safety_incident_count >= 0
        AND matched_case_count <= evaluated_case_count
        AND false_positive_case_count <= matched_case_count
    )
);

CREATE INDEX IF NOT EXISTS idx_clinical_redline_trial_redline
    ON mk_engine_clinical_redline_trial (tenant_id, redline_id, redline_version, created_at);
CREATE INDEX IF NOT EXISTS idx_clinical_redline_trial_status
    ON mk_engine_clinical_redline_trial (tenant_id, status, gate_passed, created_at);

COMMENT ON TABLE mk_engine_clinical_redline_trial IS '临床安全红线静默试运行证据：保存真实观察窗口、命中统计和上线门禁结果';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.trial_id IS '试运行证据业务 ID';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.redline_id IS '关联红线版本业务 ID';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.redline_key IS '红线稳定键';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.redline_version IS '红线内容版本号';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.status IS '试运行门禁结果：通过或未通过';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.observed_from IS '试运行观察窗口开始时间';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.observed_to IS '试运行观察窗口结束时间';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.required_silent_hours IS '红线要求的静默试运行小时数';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.actual_silent_hours IS '本次证据覆盖的实际静默小时数';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.evaluated_case_count IS '真实观察窗口内评估病例数';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.matched_case_count IS '真实观察窗口内红线命中数';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.false_positive_case_count IS '真实观察窗口内人工确认误报数';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.safety_incident_count IS '静默试运行期间发现的安全事件数';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.gate_passed IS '上线门禁是否达标';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.evidence_reference IS '试运行证据引用；不得使用伪造或占位证据';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.operator_note IS '操作者说明';
COMMENT ON COLUMN mk_engine_clinical_redline_trial.trace_id IS '创建追踪 ID';
