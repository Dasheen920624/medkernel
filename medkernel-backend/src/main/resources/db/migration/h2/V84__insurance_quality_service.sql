-- MedKernel v1.0 GA · SVC-QUALITY-02 病案医保服务包（H2）
-- ROLLBACK：如需回滚，先导出病案内涵、DRG/DIP 与医保审核证据，再删除本迁移三张 mk_quality_* 表。

CREATE TABLE IF NOT EXISTS mk_quality_case_review (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id               VARCHAR(128)  NOT NULL,
    tenant_id               VARCHAR(64)   NOT NULL,
    context_snapshot_id     VARCHAR(64)   NOT NULL,
    patient_id              VARCHAR(64)   NOT NULL,
    encounter_id            VARCHAR(64)   NULL,
    department_id           VARCHAR(64)   NOT NULL,
    scenario_code           VARCHAR(64)   NOT NULL,
    package_version         VARCHAR(64)   NULL,
    review_status           VARCHAR(32)   NOT NULL,
    evaluation_run_id       VARCHAR(64)   NOT NULL,
    result_count            INT           NOT NULL DEFAULT 0,
    finding_count           INT           NOT NULL DEFAULT 0,
    task_count              INT           NOT NULL DEFAULT 0,
    model_status            VARCHAR(32)   NOT NULL,
    model_downgrade_reason  VARCHAR(128)  NULL,
    evidence_summary        CLOB          NOT NULL,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                VARCHAR(128)  NULL,
    CONSTRAINT uk_quality_case_review_id UNIQUE (tenant_id, review_id),
    CONSTRAINT uk_quality_case_review_snapshot UNIQUE (tenant_id, context_snapshot_id, scenario_code, package_version),
    CONSTRAINT ck_quality_case_review_status CHECK (review_status IN ('PASS','NON_COMPLIANT')),
    CONSTRAINT ck_quality_case_review_model CHECK (model_status IN ('MODEL_DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_quality_case_review_tenant_status
    ON mk_quality_case_review (tenant_id, review_status, created_at);
CREATE INDEX IF NOT EXISTS idx_quality_case_review_department
    ON mk_quality_case_review (tenant_id, department_id, review_status);

CREATE TABLE IF NOT EXISTS mk_quality_drg_grouping (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grouping_id             VARCHAR(128)  NOT NULL,
    tenant_id               VARCHAR(64)   NOT NULL,
    context_snapshot_id     VARCHAR(64)   NOT NULL,
    patient_id              VARCHAR(64)   NOT NULL,
    encounter_id            VARCHAR(64)   NULL,
    department_id           VARCHAR(64)   NOT NULL,
    grouper_version         VARCHAR(64)   NOT NULL,
    expected_group_code     VARCHAR(64)   NOT NULL,
    actual_group_code       VARCHAR(64)   NOT NULL,
    grouping_status         VARCHAR(32)   NOT NULL,
    explanation             CLOB          NOT NULL,
    evidence_summary        CLOB          NOT NULL,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                VARCHAR(128)  NULL,
    CONSTRAINT uk_quality_drg_grouping_id UNIQUE (tenant_id, grouping_id),
    CONSTRAINT uk_quality_drg_grouping_snapshot UNIQUE (tenant_id, context_snapshot_id, grouper_version),
    CONSTRAINT ck_quality_drg_grouping_status CHECK (grouping_status IN ('MATCHED','MISMATCHED'))
);

CREATE INDEX IF NOT EXISTS idx_quality_drg_grouping_tenant_status
    ON mk_quality_drg_grouping (tenant_id, grouping_status, created_at);
CREATE INDEX IF NOT EXISTS idx_quality_drg_grouping_department
    ON mk_quality_drg_grouping (tenant_id, department_id, grouping_status);

CREATE TABLE IF NOT EXISTS mk_quality_insurance_issue (
    id                      BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    issue_id                VARCHAR(128)  NOT NULL,
    tenant_id               VARCHAR(64)   NOT NULL,
    context_snapshot_id     VARCHAR(64)   NOT NULL,
    claim_id                VARCHAR(64)   NOT NULL,
    patient_id              VARCHAR(64)   NOT NULL,
    encounter_id            VARCHAR(64)   NULL,
    department_id           VARCHAR(64)   NULL,
    issue_type              VARCHAR(32)   NOT NULL,
    severity                VARCHAR(32)   NOT NULL,
    status                  VARCHAR(32)   NOT NULL DEFAULT 'OPEN',
    rule_code               VARCHAR(128)  NOT NULL,
    rule_version            VARCHAR(64)   NOT NULL,
    claim_amount            DECIMAL(18,2) NULL,
    threshold_amount        DECIMAL(18,2) NULL,
    evidence_summary        CLOB          NOT NULL,
    evaluation_run_id       VARCHAR(64)   NULL,
    finding_id              VARCHAR(64)   NULL,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                VARCHAR(128)  NULL,
    CONSTRAINT uk_quality_insurance_issue_id UNIQUE (tenant_id, issue_id),
    CONSTRAINT uk_quality_insurance_issue_source UNIQUE (tenant_id, context_snapshot_id, claim_id, issue_type, rule_code, rule_version),
    CONSTRAINT ck_quality_insurance_issue_type CHECK (issue_type IN ('DRG','CODING','FEE','INSURANCE')),
    CONSTRAINT ck_quality_insurance_issue_severity CHECK (severity IN ('P0','P1','P2','P3')),
    CONSTRAINT ck_quality_insurance_issue_status CHECK (status IN ('OPEN','RECTIFICATION_CREATED','CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_quality_insurance_issue_tenant_status
    ON mk_quality_insurance_issue (tenant_id, status, severity, created_at);
CREATE INDEX IF NOT EXISTS idx_quality_insurance_issue_department
    ON mk_quality_insurance_issue (tenant_id, department_id, status);
CREATE INDEX IF NOT EXISTS idx_quality_insurance_issue_claim
    ON mk_quality_insurance_issue (tenant_id, claim_id, rule_code);

COMMENT ON TABLE mk_quality_case_review IS 'SVC-QUALITY-02 病案内涵质控结果表，复用评估运行并保存病案级证据';
COMMENT ON TABLE mk_quality_drg_grouping IS 'SVC-QUALITY-02 DRG/DIP 入组核对结果表，保存版本化分组解释';
COMMENT ON TABLE mk_quality_insurance_issue IS 'SVC-QUALITY-02 医保病案问题表，保存编码、费用、入组和医保违规证据';
COMMENT ON COLUMN mk_quality_case_review.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_quality_case_review.context_snapshot_id IS '病案上下文快照 ID';
COMMENT ON COLUMN mk_quality_case_review.review_status IS '病案内涵质控状态';
COMMENT ON COLUMN mk_quality_drg_grouping.grouper_version IS 'DRG/DIP 分组器版本';
COMMENT ON COLUMN mk_quality_drg_grouping.grouping_status IS '入组核对状态';
COMMENT ON COLUMN mk_quality_insurance_issue.claim_id IS '医保结算事实 ID';
COMMENT ON COLUMN mk_quality_insurance_issue.issue_type IS '医保病案问题类型';
COMMENT ON COLUMN mk_quality_insurance_issue.evidence_summary IS '可追溯病历与结算证据摘要';
COMMENT ON COLUMN mk_quality_insurance_issue.trace_id IS '链路追踪 ID';
