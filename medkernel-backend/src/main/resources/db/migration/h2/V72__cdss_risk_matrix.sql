-- MedKernel v1.0 GA · OPT-03 CDSS 风险分级矩阵（H2 PostgreSQL 兼容模式）
-- ROLLBACK: 若需回退，先归档 mk_engine_cdss_risk_matrix 变更审计，再删除 recommendation_card 的风险矩阵追溯列与 mk_engine_cdss_risk_matrix 表；已发推荐卡需保留导出证据。

CREATE TABLE IF NOT EXISTS mk_engine_cdss_risk_matrix (
    id                       BIGSERIAL PRIMARY KEY,
    matrix_id                VARCHAR(64)   NOT NULL,
    tenant_id                VARCHAR(64)   NOT NULL,
    trigger_point            VARCHAR(64)   NOT NULL,
    severity_level           VARCHAR(32)   NOT NULL,
    automation_level         VARCHAR(32)   NOT NULL,
    risk_level               VARCHAR(32)   NOT NULL,
    review_requirement       VARCHAR(64)   NOT NULL,
    silent_run_hours         INT           NOT NULL DEFAULT 0,
    release_gate             VARCHAR(128)  NOT NULL,
    auto_execution_allowed   BOOLEAN       NOT NULL DEFAULT FALSE,
    samd_classification      VARCHAR(64)   NULL,
    regulatory_evidence      VARCHAR(512)  NULL,
    status                   VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    matrix_version           VARCHAR(64)   NOT NULL,
    explanation              VARCHAR(1024) NOT NULL,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                 VARCHAR(128)  NULL,
    CONSTRAINT uk_cdss_risk_matrix_id UNIQUE (matrix_id),
    CONSTRAINT uk_cdss_risk_matrix_scope_version UNIQUE (
        tenant_id, trigger_point, severity_level, automation_level, status, matrix_version
    ),
    CONSTRAINT ck_cdss_risk_matrix_severity CHECK (severity_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_cdss_risk_matrix_auto CHECK (automation_level IN ('INFORM_ONLY','INTERRUPTIVE','AUTOMATED')),
    CONSTRAINT ck_cdss_risk_matrix_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_cdss_risk_matrix_review CHECK (
        review_requirement IN ('OPTIONAL_REVIEW','PHYSICIAN_CONFIRMATION','DUAL_REVIEW')
    ),
    CONSTRAINT ck_cdss_risk_matrix_status CHECK (status IN ('DRAFT','PUBLISHED','ACTIVE','RETIRED')),
    CONSTRAINT ck_cdss_risk_matrix_auto_exec CHECK (auto_execution_allowed IN (TRUE, FALSE))
);

CREATE INDEX IF NOT EXISTS idx_cdss_risk_matrix_active
    ON mk_engine_cdss_risk_matrix (tenant_id, trigger_point, severity_level, automation_level, status);
CREATE INDEX IF NOT EXISTS idx_cdss_risk_matrix_version
    ON mk_engine_cdss_risk_matrix (tenant_id, matrix_version, status, updated_at);

ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS risk_matrix_id VARCHAR(64) NULL;
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS risk_matrix_version VARCHAR(64) NULL;
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS automation_level VARCHAR(32) NOT NULL DEFAULT 'INFORM_ONLY';
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS review_requirement VARCHAR(64) NOT NULL DEFAULT 'OPTIONAL_REVIEW';
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS silent_run_hours INT NOT NULL DEFAULT 0;
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS release_gate VARCHAR(128) NOT NULL DEFAULT 'STANDARD_CHANGE_REVIEW';
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS auto_execution_allowed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS samd_classification VARCHAR(64) NULL;
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS regulatory_evidence VARCHAR(512) NULL;
ALTER TABLE recommendation_card ADD COLUMN IF NOT EXISTS risk_matrix_explanation VARCHAR(1024) NULL;

ALTER TABLE recommendation_card ADD CONSTRAINT ck_rec_card_automation
    CHECK (automation_level IN ('INFORM_ONLY','INTERRUPTIVE','AUTOMATED'));
ALTER TABLE recommendation_card ADD CONSTRAINT ck_rec_card_review
    CHECK (review_requirement IN ('OPTIONAL_REVIEW','PHYSICIAN_CONFIRMATION','DUAL_REVIEW'));
ALTER TABLE recommendation_card ADD CONSTRAINT ck_rec_card_auto_execution
    CHECK (auto_execution_allowed IN (TRUE, FALSE));

CREATE INDEX IF NOT EXISTS idx_rec_card_risk_matrix
    ON recommendation_card (tenant_id, risk_matrix_version, automation_level, created_at);

COMMENT ON TABLE mk_engine_cdss_risk_matrix IS 'CDSS 风险分级矩阵：按触发点、危害严重度和自动化程度决定推荐输出风险级别、审核强度和上线门槛';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.matrix_id IS '矩阵规则 ID（业务键）';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.trigger_point IS 'CDS Hooks 触发点：patient-view/order-sign/medication-prescribe/result-review/discharge-sign/followup-alert';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.severity_level IS '输入危害严重度：LOW/MEDIUM/HIGH/CRITICAL';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.automation_level IS '自动化程度：INFORM_ONLY 仅提示 / INTERRUPTIVE 打断式 / AUTOMATED 自动化输出';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.risk_level IS '矩阵输出风险级别：LOW/MEDIUM/HIGH/CRITICAL';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.review_requirement IS '审核要求：OPTIONAL_REVIEW 可选复核 / PHYSICIAN_CONFIRMATION 医师确认 / DUAL_REVIEW 双人复核';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.silent_run_hours IS '静默试运行小时数门槛';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.release_gate IS '上线门槛编码，供 OPT-04 红线与发布门禁引用';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.auto_execution_allowed IS '是否允许自动执行；医疗安全主线默认 FALSE';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.samd_classification IS 'NMPA SaMD 分类预留字段；当前仅记录预留状态，不代表已完成监管认定';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.regulatory_evidence IS '监管证据预留字段：记录未来 SaMD 路径所需证据要求';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.matrix_version IS '矩阵版本号';
COMMENT ON COLUMN mk_engine_cdss_risk_matrix.explanation IS '分级依据说明';
COMMENT ON COLUMN recommendation_card.risk_matrix_id IS '命中的 CDSS 风险矩阵规则 ID';
COMMENT ON COLUMN recommendation_card.risk_matrix_version IS '命中的 CDSS 风险矩阵版本';
COMMENT ON COLUMN recommendation_card.automation_level IS '本推荐卡采用的 CDSS 自动化程度';
COMMENT ON COLUMN recommendation_card.review_requirement IS '本推荐卡的人工审核要求';
COMMENT ON COLUMN recommendation_card.silent_run_hours IS '本推荐卡对应的静默试运行门槛小时数';
COMMENT ON COLUMN recommendation_card.release_gate IS '本推荐卡对应的上线门槛编码';
COMMENT ON COLUMN recommendation_card.auto_execution_allowed IS '本推荐卡是否允许自动执行；当前医疗安全主线禁止自动执行';
COMMENT ON COLUMN recommendation_card.samd_classification IS 'NMPA SaMD 分类预留字段；不代表已完成监管认定';
COMMENT ON COLUMN recommendation_card.regulatory_evidence IS '监管证据预留字段';
COMMENT ON COLUMN recommendation_card.risk_matrix_explanation IS '风险矩阵分级解释';
