-- MedKernel 第二阶段 P2-A · LLM-07 医学回归评测基准集与运行结果（PostgreSQL）

CREATE TABLE IF NOT EXISTS mk_llm_regression_case (
    id                BIGSERIAL     PRIMARY KEY,
    tenant_id         VARCHAR(64)   NOT NULL,
    capability_code   VARCHAR(64)   NOT NULL,
    case_domain       VARCHAR(32)   NOT NULL DEFAULT 'general',
    case_input        VARCHAR(2000) NOT NULL,
    expected_phrase   VARCHAR(512)  NOT NULL,
    expected_terms_json       VARCHAR(2000) NOT NULL DEFAULT '[]',
    forbidden_assertions_json VARCHAR(2000) NOT NULL DEFAULT '[]',
    min_score         INTEGER       NOT NULL DEFAULT 100,
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
    CONSTRAINT ck_mk_llm_regression_case_enabled CHECK (enabled_flag IN ('Y', 'N')),
    CONSTRAINT ck_mk_llm_regression_case_score CHECK (min_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_mk_llm_regression_case_tenant ON mk_llm_regression_case (tenant_id, capability_code);
CREATE INDEX idx_mk_llm_regression_case_domain ON mk_llm_regression_case (tenant_id, capability_code, case_domain);

CREATE TABLE IF NOT EXISTS mk_llm_eval_run (
    id                     BIGSERIAL    PRIMARY KEY,
    tenant_id              VARCHAR(64)  NOT NULL,
    provider_code          VARCHAR(64)  NOT NULL,
    model_version          VARCHAR(64)  NOT NULL,
    capability_code        VARCHAR(64)  NULL,
    prompt_version         VARCHAR(128) NULL,
    tool_version           VARCHAR(128) NULL,
    total_cases            INTEGER      NOT NULL DEFAULT 0,
    passed_cases           INTEGER      NOT NULL DEFAULT 0,
    failed_cases           INTEGER      NOT NULL DEFAULT 0,
    quality_score          DECIMAL(5,2) NULL,
    terminology_score      DECIMAL(5,2) NULL,
    fake_citation_detected CHAR(1)      NOT NULL DEFAULT 'N',
    red_line_breach        CHAR(1)      NOT NULL DEFAULT 'N',
    hallucination_detected CHAR(1)      NOT NULL DEFAULT 'N',
    status                 VARCHAR(24)  NOT NULL,
    case_summary_json      VARCHAR(4000) NOT NULL DEFAULT '[]',
    reviewer               VARCHAR(64)  NULL,
    signed_at              TIMESTAMPTZ  NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by             VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_mk_llm_eval_run_status CHECK (status IN ('PASSED', 'FAILED', 'PENDING_REVIEW')),
    CONSTRAINT ck_mk_llm_eval_run_hallucination CHECK (hallucination_detected IN ('Y', 'N')),
    CONSTRAINT ck_mk_llm_eval_run_quality_score CHECK (
        (quality_score IS NULL OR quality_score BETWEEN 0 AND 100)
        AND (terminology_score IS NULL OR terminology_score BETWEEN 0 AND 100)
    )
);

CREATE INDEX idx_mk_llm_eval_run_lookup ON mk_llm_eval_run (tenant_id, provider_code, model_version, status);
CREATE INDEX idx_mk_llm_eval_run_capability ON mk_llm_eval_run (tenant_id, capability_code, model_version, created_at);

COMMENT ON TABLE mk_llm_regression_case IS '医学回归评测基准用例：登记输入、期望短语、红线类型和真实来源引用，不含真实患者数据';
COMMENT ON COLUMN mk_llm_regression_case.case_domain IS 'AI 质量评测维度：dictionary/rule/pathway/recommendation/explanation/terminology/general';
COMMENT ON COLUMN mk_llm_regression_case.expected_terms_json IS '中文术语专项期望命中词 JSON 数组';
COMMENT ON COLUMN mk_llm_regression_case.forbidden_assertions_json IS '幻觉拦截禁用断言或编造编码 JSON 数组';
COMMENT ON COLUMN mk_llm_regression_case.min_score IS '该用例最低通过分，0-100';
COMMENT ON COLUMN mk_llm_regression_case.source_reference IS '用例来源引用，必须指向已审红线、来源版本或真实评测资料锚点';
COMMENT ON TABLE mk_llm_eval_run IS '模型版本医学回归与 AI 质量评测运行结果：上线门禁、幻觉拦截、术语质量和版本趋势依据';
COMMENT ON COLUMN mk_llm_eval_run.capability_code IS '被评测模型能力码';
COMMENT ON COLUMN mk_llm_eval_run.prompt_version IS '本次评测绑定的 prompt 版本号';
COMMENT ON COLUMN mk_llm_eval_run.tool_version IS '本次评测绑定的 tool 版本号';
COMMENT ON COLUMN mk_llm_eval_run.quality_score IS 'AI 质量总分，0-100';
COMMENT ON COLUMN mk_llm_eval_run.terminology_score IS '中文术语专项分，0-100';
COMMENT ON COLUMN mk_llm_eval_run.hallucination_detected IS '是否命中幻觉拦截：Y/N';
COMMENT ON COLUMN mk_llm_eval_run.case_summary_json IS '逐用例评分、失败原因和幻觉标记摘要 JSON';
