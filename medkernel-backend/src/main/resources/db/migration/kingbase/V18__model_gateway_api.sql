-- MedKernel v1.0 GA · GA-ENG-API-12 模型能力网关 API（Kingbase）

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
    id                        BIGSERIAL PRIMARY KEY,
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
    confidence                DOUBLE PRECISION NULL,
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
    id                        BIGSERIAL PRIMARY KEY,
    tenant_id                 VARCHAR(64)   NOT NULL,
    capability_code           VARCHAR(64)   NOT NULL,
    scope_type                VARCHAR(32)   NOT NULL,
    scope_ref                 VARCHAR(128)  NOT NULL,
    route_strategy            VARCHAR(32)   NOT NULL DEFAULT 'BASELINE',
    desensitize_strategy      VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT',
    expected_schema           TEXT          NULL,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                VARCHAR(64)   NOT NULL DEFAULT 'system',
    CONSTRAINT ck_model_policy_scope CHECK (scope_type IN ('TENANT','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT','WARD')),
    CONSTRAINT uk_model_policy_scope UNIQUE (tenant_id, capability_code, scope_type, scope_ref)
);

COMMENT ON TABLE model_capability_task IS '模型网关调用任务表';
COMMENT ON TABLE model_capability_definition IS '平台模型能力目录';
COMMENT ON COLUMN model_capability_definition.capability_code IS '模型能力代码';
COMMENT ON COLUMN model_capability_definition.display_name IS '能力中文名称';
COMMENT ON COLUMN model_capability_definition.description IS '能力业务说明';
COMMENT ON COLUMN model_capability_definition.category IS '能力业务分类';
COMMENT ON COLUMN model_capability_definition.enabled_flag IS '是否启用';
COMMENT ON COLUMN model_capability_definition.sort_order IS '展示顺序';
COMMENT ON COLUMN model_capability_task.task_id IS '任务ID';
COMMENT ON COLUMN model_capability_task.tenant_id IS '租户ID';
COMMENT ON COLUMN model_capability_task.capability_code IS '能力标识代码';
COMMENT ON COLUMN model_capability_task.input_hash IS '原始输入内容哈希';
COMMENT ON COLUMN model_capability_task.input_summary IS '脱敏后的输入内容摘要';
COMMENT ON COLUMN model_capability_task.output_content IS '模型推理或基线返回的结构化或自由文本输出内容';
COMMENT ON COLUMN model_capability_task.model_mode IS '运行模式(B0无模型,B1模型辅助,B2探索模式)';
COMMENT ON COLUMN model_capability_task.model_version IS '调用的模型名称及版本';
COMMENT ON COLUMN model_capability_task.prompt_version IS '调用的提示词版本';
COMMENT ON COLUMN model_capability_task.source_citations IS '模型生成候选引用的文献或事实文献来源';
COMMENT ON COLUMN model_capability_task.confidence IS '输出结果的可信度/置信度评分';
COMMENT ON COLUMN model_capability_task.risk_level IS '输出结果的医疗安全风险级别(LOW,MEDIUM,HIGH)';
COMMENT ON COLUMN model_capability_task.fallback_used IS '是否使用了B0无模型基线路径回退降级';
COMMENT ON COLUMN model_capability_task.fallback_reason IS '降级回退具体触发原因';
COMMENT ON COLUMN model_capability_task.time_cost_ms IS '模型推理或处理耗时（毫秒）';
COMMENT ON COLUMN model_capability_task.status IS '任务流转状态(PENDING,RUNNING,SUCCESS,FAILED,DEGRADED)';
COMMENT ON COLUMN model_capability_task.trace_id IS '追踪ID';

COMMENT ON TABLE model_capability_policy IS '场景模型路由与脱敏策略配置表';
COMMENT ON COLUMN model_capability_policy.tenant_id IS '租户ID';
COMMENT ON COLUMN model_capability_policy.capability_code IS '能力标识代码';
COMMENT ON COLUMN model_capability_policy.scope_type IS '策略作用域类型：租户、集团、医院、院区、站点、科室或病区';
COMMENT ON COLUMN model_capability_policy.scope_ref IS '策略作用域引用ID，按组织继承链逐级解析';
COMMENT ON COLUMN model_capability_policy.route_strategy IS '模型路由策略(DISABLED禁用,BASELINE基线B0,LOCAL_MODEL本地,EXTERNAL_MODEL外部)';
COMMENT ON COLUMN model_capability_policy.desensitize_strategy IS '数据脱敏策略代码';
COMMENT ON COLUMN model_capability_policy.expected_schema IS '期待输出匹配的JSON Schema结构约束';
