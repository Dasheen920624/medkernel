-- MedKernel 第二阶段 P2-B · LLM-05 全业务模型增强接入矩阵（PostgreSQL）
-- 平台全局「模型网关全局目录」增强接入图谱：业务点 ↔ 能力码 ↔ B0 路径 ↔ 接入状态。
-- ROLLBACK：确认无引用后 DROP TABLE mk_llm_enhancement_matrix。

CREATE TABLE IF NOT EXISTS mk_llm_enhancement_matrix (
    id              BIGSERIAL    PRIMARY KEY,
    business_point  VARCHAR(64)  NOT NULL,
    business_name   VARCHAR(128) NOT NULL,
    capability_code VARCHAR(64)  NULL,
    b0_path         VARCHAR(512) NULL,
    access_status   VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    enabled_flag    CHAR(1)      NOT NULL DEFAULT 'Y',
    sort_order      INTEGER      NOT NULL DEFAULT 100,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    CONSTRAINT uk_mk_llm_enhancement_matrix_point UNIQUE (business_point),
    CONSTRAINT ck_mk_llm_enhancement_matrix_status CHECK (access_status IN ('PENDING', 'ACTIVE', 'DISABLED')),
    CONSTRAINT ck_mk_llm_enhancement_matrix_enabled CHECK (enabled_flag IN ('Y', 'N'))
);

CREATE INDEX idx_mk_llm_enhancement_matrix_status ON mk_llm_enhancement_matrix (access_status, sort_order);

INSERT INTO mk_llm_enhancement_matrix
    (business_point, business_name, capability_code, b0_path, access_status, sort_order)
VALUES
    ('knowledge-discovery', '临床知识关联发现', 'knowledge.discovery', '确定性知识检索与关联基线', 'ACTIVE', 10),
    ('knowledge-extract', '电子病历语义实体提取', 'knowledge.extract', '规则化结构实体抽取基线', 'ACTIVE', 20),
    ('terminology', '标准术语字典匹配映射', 'terminology.map', '确定性术语映射基线', 'ACTIVE', 30),
    ('rule', '临床规则草案拟定', 'rule.draft', '确定性规则模板基线', 'ACTIVE', 40),
    ('pathway', '临床路径草案拟定', 'pathway.draft', '确定性路径模板基线', 'ACTIVE', 50),
    ('cdss', '临床决策解释', 'cdss.explain', '确定性决策依据解释基线', 'ACTIVE', 60),
    ('quality', '病历内涵质控', 'quality.semantic-check', '确定性质控规则基线', 'ACTIVE', 70),
    ('followup', '随访草案拟定', 'followup.draft', '确定性随访模板基线', 'ACTIVE', 80),
    ('nursing', '护理知识增强', NULL, NULL, 'PENDING', 90),
    ('report', '报告解读增强', NULL, NULL, 'PENDING', 100);

COMMENT ON TABLE mk_llm_enhancement_matrix IS '全业务模型增强接入矩阵：平台全局业务点↔能力码↔B0路径↔接入状态台账，缺口诚实标注';
