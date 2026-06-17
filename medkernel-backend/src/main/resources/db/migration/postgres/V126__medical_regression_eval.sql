-- MedKernel 第二阶段 P2-A · LLM-07 医学回归评测基准集与运行结果（PostgreSQL）
-- ROLLBACK：确认无引用后依次 DROP TABLE mk_llm_eval_run / mk_llm_regression_case。

CREATE TABLE IF NOT EXISTS mk_llm_regression_case (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    case_input        VARCHAR(2000) NOT NULL,
    expected_phrase   VARCHAR(512)  NOT NULL,
    red_line_type     VARCHAR(64)   NULL,
    source_reference  VARCHAR(512)  NOT NULL,
    citation_required CHAR(1)       NOT NULL DEFAULT 'N',
    case_version      VARCHAR(32)   NOT NULL DEFAULT 'v1',
    enabled_flag      CHAR(1)       NOT NULL DEFAULT 'Y',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT ck_mk_llm_regression_case_citation CHECK (citation_required IN ('Y', 'N')),
    CONSTRAINT ck_mk_llm_regression_case_enabled CHECK (enabled_flag IN ('Y', 'N'))
);

CREATE INDEX idx_mk_llm_regression_case_tenant ON mk_llm_regression_case (tenant_id, capability_code);

CREATE TABLE IF NOT EXISTS mk_llm_eval_run (
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
    signed_at              TIMESTAMPTZ  NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by             VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_mk_llm_eval_run_status CHECK (status IN ('PASSED', 'FAILED', 'PENDING_REVIEW'))
);

CREATE INDEX idx_mk_llm_eval_run_lookup ON mk_llm_eval_run (tenant_id, provider_code, model_version, status);

COMMENT ON TABLE mk_llm_regression_case IS '医学回归评测基准用例：登记输入、期望短语、红线类型和真实来源引用，不含真实患者数据';
COMMENT ON COLUMN mk_llm_regression_case.source_reference IS '用例来源引用，必须指向已审红线、来源版本或真实评测资料锚点';
COMMENT ON TABLE mk_llm_eval_run IS '模型版本医学回归评测运行结果：上线门禁依据，记录通过率与红线判定及复核签字';
