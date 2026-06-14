-- MedKernel 第二阶段 P2-A · LLM-07 医学回归评测基准集与运行结果（KingbaseES）
-- ROLLBACK：确认无引用后依次 DROP TABLE model_eval_run / medical_regression_case。

CREATE TABLE IF NOT EXISTS medical_regression_case (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    case_input        VARCHAR(2000) NOT NULL,
    expected_phrase   VARCHAR(512)  NOT NULL,
    red_line_type     VARCHAR(32)   NULL,
    citation_required CHAR(1)       NOT NULL DEFAULT 'N',
    case_version      VARCHAR(32)   NOT NULL DEFAULT 'v1',
    enabled_flag      CHAR(1)       NOT NULL DEFAULT 'Y',
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT ck_medical_regression_case_citation CHECK (citation_required IN ('Y', 'N')),
    CONSTRAINT ck_medical_regression_case_enabled CHECK (enabled_flag IN ('Y', 'N'))
);

CREATE INDEX idx_medical_regression_case_tenant ON medical_regression_case (tenant_id, capability_code);

CREATE TABLE IF NOT EXISTS model_eval_run (
    id                     BIGSERIAL    PRIMARY KEY,
    tenant_id              VARCHAR(64)  NOT NULL,
    provider_code          VARCHAR(64)  NOT NULL,
    model_version          VARCHAR(64)  NOT NULL,
    total_cases            INTEGER      NOT NULL DEFAULT 0,
    passed_cases           INTEGER      NOT NULL DEFAULT 0,
    failed_cases           INTEGER      NOT NULL DEFAULT 0,
    fake_citation_detected CHAR(1)      NOT NULL DEFAULT 'N',
    red_line_breach        CHAR(1)      NOT NULL DEFAULT 'N',
    status                 VARCHAR(24)  NOT NULL,
    reviewer               VARCHAR(64)  NULL,
    signed_at              TIMESTAMP    NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_model_eval_run_status CHECK (status IN ('PASSED', 'FAILED', 'PENDING_REVIEW'))
);

CREATE INDEX idx_model_eval_run_lookup ON model_eval_run (tenant_id, provider_code, model_version, status);
