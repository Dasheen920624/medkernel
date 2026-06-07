-- MedKernel v1.0 GA · GA-ENG-API-12 模型能力网关 API（H2 baseline，MODE=PostgreSQL 兼容）

CREATE TABLE IF NOT EXISTS model_capability_definition (
    capability_code VARCHAR(64)  PRIMARY KEY,
    display_name    VARCHAR(120) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    category        VARCHAR(64)  NOT NULL,
    enabled_flag    CHAR(1)      NOT NULL DEFAULT 'Y',
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT ck_model_capability_definition_enabled CHECK (enabled_flag IN ('Y', 'N'))
);

INSERT INTO model_capability_definition
    (capability_code, display_name, description, category, sort_order)
VALUES
    ('knowledge.discovery', '临床知识关联发现', '从临床事实中检索并关联可信知识依据。', '知识资产', 10),
    ('knowledge.extract', '电子病历语义实体提取', '从病历文本中提取结构化临床事实。', '语义抽取', 20),
    ('terminology.map', '标准术语字典匹配映射', '将院内术语映射到标准医学术语。', '字典映射', 30),
    ('rule.draft', '临床规则草案拟定', '基于可信依据生成待人工审核的规则草案。', '规则引擎', 40),
    ('pathway.draft', '临床路径草案拟定', '生成待人工审核的路径节点与变异草案。', '路径引擎', 50),
    ('cdss.explain', '临床决策解释', '将确定性决策依据转换为可追溯说明。', '解释追溯', 60),
    ('quality.semantic-check', '病历内涵质控', '识别病历中的逻辑缺项与质控风险。', '质控改进', 70),
    ('followup.draft', '随访草案拟定', '生成待人工审核的随访计划与问卷草案。', '智能随访', 80);

CREATE TABLE IF NOT EXISTS model_capability_task (
    id                        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id                   VARCHAR(64)   NOT NULL,
    tenant_id                 VARCHAR(64)   NOT NULL,
    capability_code           VARCHAR(64)   NOT NULL,
    input_hash                VARCHAR(64)   NOT NULL,
    input_summary             VARCHAR(512)  NOT NULL,
    output_content            TEXT          NULL,
    model_mode                VARCHAR(32)   NOT NULL,
    model_version             VARCHAR(64)   NULL,
    prompt_version            VARCHAR(64)   NULL,
    source_citations          VARCHAR(1024) NULL,
    confidence                DOUBLE        NULL,
    risk_level                VARCHAR(32)   NULL,
    fallback_used             BOOLEAN       NOT NULL DEFAULT FALSE,
    fallback_reason           VARCHAR(255)  NULL,
    time_cost_ms              BIGINT        NOT NULL DEFAULT 0,
    status                    VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    trace_id                  VARCHAR(128)  NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_model_task_id UNIQUE (task_id)
);

CREATE INDEX idx_model_task_tenant ON model_capability_task (tenant_id, capability_code);

CREATE TABLE IF NOT EXISTS model_capability_policy (
    id                        BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                 VARCHAR(64)   NOT NULL,
    capability_code           VARCHAR(64)   NOT NULL,
    route_strategy            VARCHAR(32)   NOT NULL DEFAULT 'BASELINE',
    desensitize_strategy      VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT',
    expected_schema           TEXT          NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT uk_model_policy_tenant UNIQUE (tenant_id, capability_code)
);

COMMENT ON TABLE model_capability_definition IS '平台模型能力目录';
