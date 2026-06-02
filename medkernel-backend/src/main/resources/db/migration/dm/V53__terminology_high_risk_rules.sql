-- MedKernel v1.0 GA · TERM-01 术语高危近似规则（达梦）
-- ROLLBACK：确认无依赖该规则生成的 PENDING 高危候选后，删除 mk_term_high_risk_rule 表。

CREATE TABLE mk_term_high_risk_rule (
    id             NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR2(64)  NOT NULL,
    rule_code      VARCHAR2(64)  NOT NULL,
    rule_type      VARCHAR2(32)  NOT NULL,
    category       VARCHAR2(32)  NULL,
    left_terms     VARCHAR2(1024) NULL,
    right_terms    VARCHAR2(1024) NULL,
    unit_terms     VARCHAR2(512) NULL,
    scale_ratio    NUMBER(18,6)  NULL,
    evidence_text  VARCHAR2(1024) NOT NULL,
    status         VARCHAR2(16)  DEFAULT 'ACTIVE' NOT NULL,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by     VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by     VARCHAR2(64)  DEFAULT 'system' NOT NULL,
    CONSTRAINT uk_mk_term_high_risk_rule_code UNIQUE (tenant_id, rule_code),
    CONSTRAINT ck_mk_term_high_risk_rule_type CHECK (rule_type IN
        ('MUTUALLY_EXCLUSIVE_TERMS','DOSE_MAGNITUDE','UNIT_STRENGTH')),
    CONSTRAINT ck_mk_term_high_risk_rule_category CHECK (category IS NULL OR category IN
        ('DIAGNOSIS','PROCEDURE','DRUG','DEVICE','LAB','EXAM','ORDER','INSURANCE','DEPARTMENT','DOCUMENT','FOLLOWUP','OTHER')),
    CONSTRAINT ck_mk_term_high_risk_rule_status CHECK (status IN ('ACTIVE','DISABLED'))
);

CREATE INDEX idx_mk_term_high_risk_rule_tenant_status ON mk_term_high_risk_rule (tenant_id, status);
CREATE INDEX idx_mk_term_high_risk_rule_category ON mk_term_high_risk_rule (tenant_id, category, status);

INSERT INTO mk_term_high_risk_rule (tenant_id, rule_code, rule_type, category, left_terms, right_terms, unit_terms, scale_ratio, evidence_text)
VALUES ('SYSTEM', 'MED-C1-TROPONIN-TI', 'MUTUALLY_EXCLUSIVE_TERMS', 'LAB',
        '肌钙蛋白t|ctnt|troponint|tnt', '肌钙蛋白i|ctni|troponini|tni', NULL, NULL, '肌钙蛋白 T/I 高危近似');
INSERT INTO mk_term_high_risk_rule (tenant_id, rule_code, rule_type, category, left_terms, right_terms, unit_terms, scale_ratio, evidence_text)
VALUES ('SYSTEM', 'MED-C1-K-NA', 'MUTUALLY_EXCLUSIVE_TERMS', 'DRUG',
        '钾|k|k+|potassium|氯化钾|kcl', '钠|na|na+|sodium|氯化钠|nacl', NULL, NULL, '钾/钠高危近似');
INSERT INTO mk_term_high_risk_rule (tenant_id, rule_code, rule_type, category, left_terms, right_terms, unit_terms, scale_ratio, evidence_text)
VALUES ('SYSTEM', 'MED-C1-LEFT-RIGHT', 'MUTUALLY_EXCLUSIVE_TERMS', NULL,
        '左|left', '右|right', NULL, NULL, '左/右部位高危近似');
INSERT INTO mk_term_high_risk_rule (tenant_id, rule_code, rule_type, category, left_terms, right_terms, unit_terms, scale_ratio, evidence_text)
VALUES ('SYSTEM', 'MED-C1-DOSE-10X', 'DOSE_MAGNITUDE', 'DRUG',
        NULL, NULL, 'mg|毫克', 10, '剂量量级 10 倍高危近似');
INSERT INTO mk_term_high_risk_rule (tenant_id, rule_code, rule_type, category, left_terms, right_terms, unit_terms, scale_ratio, evidence_text)
VALUES ('SYSTEM', 'MED-C1-INSULIN-UML', 'UNIT_STRENGTH', 'DRUG',
        '胰岛素|insulin', NULL, 'u/ml|iu/ml|单位/ml|单位每毫升', NULL, '胰岛素 U/mL 单位高危近似');

COMMENT ON TABLE mk_term_high_risk_rule IS '术语高危近似规则：用于识别必须人工逐条二次确认的高危候选';
COMMENT ON COLUMN mk_term_high_risk_rule.tenant_id IS '租户 ID；SYSTEM 表示平台全局安全底线规则';
COMMENT ON COLUMN mk_term_high_risk_rule.rule_code IS '规则编码，租户内唯一';
COMMENT ON COLUMN mk_term_high_risk_rule.rule_type IS '规则类型：MUTUALLY_EXCLUSIVE_TERMS 互斥词组 / DOSE_MAGNITUDE 剂量量级 / UNIT_STRENGTH 单位强度';
COMMENT ON COLUMN mk_term_high_risk_rule.category IS '适用术语分类；为空表示跨分类通用';
COMMENT ON COLUMN mk_term_high_risk_rule.left_terms IS '左侧词组，使用竖线等分隔，命中后与右侧词组互斥判断';
COMMENT ON COLUMN mk_term_high_risk_rule.right_terms IS '右侧词组，使用竖线等分隔，命中后与左侧词组互斥判断';
COMMENT ON COLUMN mk_term_high_risk_rule.unit_terms IS '剂量或单位词组，用于剂量量级和单位强度判断';
COMMENT ON COLUMN mk_term_high_risk_rule.scale_ratio IS '剂量量级阈值，例如 10 表示十倍及以上差异';
COMMENT ON COLUMN mk_term_high_risk_rule.evidence_text IS '命中证据文案，展示给审核人并写入候选证据';
COMMENT ON COLUMN mk_term_high_risk_rule.status IS '规则状态：ACTIVE 可用 / DISABLED 停用';
