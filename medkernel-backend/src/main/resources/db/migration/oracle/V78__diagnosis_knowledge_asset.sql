-- MedKernel CDSS · 诊断知识资产建模（Spec 1 / Plan A）（Oracle）
-- 诊断身份/版本复用 knowledge_identity / knowledge_asset_version（domain=DIAGNOSIS）；本迁移建 5 张结构化子表，并放开 domain CHECK 收纳 DIAGNOSIS。
-- ROLLBACK: 确认无 domain='DIAGNOSIS' 的 knowledge_identity 与任何 mk_diagnosis_* 数据后，先恢复 ck_knowledge_identity_domain 原 10 值约束，再删除 5 张 mk_diagnosis_* 表。

-- 放开知识身份 domain 约束，收纳 DIAGNOSIS（V3 基线只含 10 值，诊断身份否则插不进库）
ALTER TABLE knowledge_identity DROP CONSTRAINT ck_knowledge_identity_domain;
ALTER TABLE knowledge_identity ADD CONSTRAINT ck_knowledge_identity_domain CHECK (domain IN
    ('GUIDELINE','DRUG','PATHWAY_KNOWLEDGE','NURSING','REPORT','TCM','PROTOCOL','POLICY','LITERATURE','OTHER','DIAGNOSIS'));

CREATE TABLE mk_diagnosis_criterion (
    id                   NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            VARCHAR2(64)   NOT NULL,
    diagnosis_version_id NUMBER(19)     NOT NULL,
    finding_term_code    VARCHAR2(64)   NOT NULL,
    direction            VARCHAR2(16)   NOT NULL,
    weight               VARCHAR2(8)    NOT NULL,
    value_constraint     VARCHAR2(512)  NULL,
    temporal_constraint  VARCHAR2(256)  NULL,
    citation_id          NUMBER(19)     NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR2(64)   NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by           VARCHAR2(64)   NOT NULL,
    trace_id             VARCHAR2(128)  NULL,
    CONSTRAINT ck_mk_diagnosis_criterion_dir CHECK (direction IN ('SUPPORTING','REFUTING','REQUIRED','EXCLUSION')),
    CONSTRAINT ck_mk_diagnosis_criterion_weight CHECK (weight IN ('MAJOR','MINOR'))
);
CREATE INDEX idx_mk_diagnosis_criterion_finding ON mk_diagnosis_criterion (tenant_id, finding_term_code);
CREATE INDEX idx_mk_diagnosis_criterion_version ON mk_diagnosis_criterion (tenant_id, diagnosis_version_id);

CREATE TABLE mk_diagnosis_differential (
    id                       NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                VARCHAR2(64)   NOT NULL,
    diagnosis_version_id     NUMBER(19)     NOT NULL,
    differential_identity_id NUMBER(19)     NOT NULL,
    key_point                VARCHAR2(1024) NULL,
    suggested_workup         VARCHAR2(512)  NULL,
    bidirectional            NUMBER(1)      DEFAULT 1 NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by               VARCHAR2(64)   NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by               VARCHAR2(64)   NOT NULL,
    trace_id                 VARCHAR2(128)  NULL
);
CREATE INDEX idx_mk_diagnosis_differential_version ON mk_diagnosis_differential (tenant_id, diagnosis_version_id);

CREATE TABLE mk_diagnosis_care_pointer (
    id                   NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            VARCHAR2(64)   NOT NULL,
    diagnosis_version_id NUMBER(19)     NOT NULL,
    pointer_type         VARCHAR2(16)   NOT NULL,
    target_ref           VARCHAR2(128)  NOT NULL,
    is_soft              NUMBER(1)      DEFAULT 1 NOT NULL,
    description          VARCHAR2(512)  NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR2(64)   NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by           VARCHAR2(64)   NOT NULL,
    trace_id             VARCHAR2(128)  NULL,
    CONSTRAINT ck_mk_diagnosis_pointer_type CHECK (pointer_type IN ('TREATMENT','WORKUP','PATHWAY'))
);
CREATE INDEX idx_mk_diagnosis_pointer_version ON mk_diagnosis_care_pointer (tenant_id, diagnosis_version_id);

CREATE TABLE mk_diagnosis_test_case (
    id                   NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            VARCHAR2(64)   NOT NULL,
    diagnosis_version_id NUMBER(19)     NOT NULL,
    case_code            VARCHAR2(64)   NOT NULL,
    findings             CLOB           NOT NULL,
    expected_identity_id NUMBER(19)     NOT NULL,
    expected_confidence  VARCHAR2(16)   NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR2(64)   NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by           VARCHAR2(64)   NOT NULL,
    trace_id             VARCHAR2(128)  NULL,
    CONSTRAINT ck_mk_diagnosis_testcase_conf CHECK (expected_confidence IN ('STRONG','MODERATE','WEAK','EXCLUDE')),
    CONSTRAINT uk_mk_diagnosis_testcase UNIQUE (tenant_id, diagnosis_version_id, case_code)
);
CREATE INDEX idx_mk_diagnosis_testcase_version ON mk_diagnosis_test_case (tenant_id, diagnosis_version_id);

CREATE TABLE mk_diagnosis_confidence_policy (
    id                   NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            VARCHAR2(64)   NOT NULL,
    scope_key            VARCHAR2(128)  NOT NULL,
    strong_min_major     NUMBER(10)     DEFAULT 2 NOT NULL,
    require_all_required  NUMBER(1)     DEFAULT 1 NOT NULL,
    moderate_min_hits    NUMBER(10)     DEFAULT 1 NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by           VARCHAR2(64)   NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by           VARCHAR2(64)   NOT NULL,
    trace_id             VARCHAR2(128)  NULL,
    CONSTRAINT uk_mk_diagnosis_confpolicy UNIQUE (tenant_id, scope_key)
);

COMMENT ON TABLE mk_diagnosis_criterion IS '诊断标准：支持/反对/必需/排除某诊断的发现项（引用标准术语编码）及权重';
COMMENT ON COLUMN mk_diagnosis_criterion.finding_term_code IS '发现项标准术语编码（TERM-01），不写死中文';
COMMENT ON COLUMN mk_diagnosis_criterion.direction IS '方向：SUPPORTING 支持 / REFUTING 反对 / REQUIRED 必需 / EXCLUSION 排除';
COMMENT ON COLUMN mk_diagnosis_criterion.temporal_constraint IS '时序/趋势约束（可选，求值留后续阶段接 RuleDslEvaluator，Spec 1 命中到编码级）';
COMMENT ON TABLE mk_diagnosis_differential IS '鉴别清单：与本诊断需鉴别的疾病、鉴别要点与建议补充检查';
COMMENT ON TABLE mk_diagnosis_care_pointer IS '诊疗指针：确诊后指向治疗/检查（规则·知识）或专病路径（恒软建议）';
COMMENT ON TABLE mk_diagnosis_test_case IS '诊断测试病例：发现集→期望候选/置信，作为发布门禁回归集';
COMMENT ON TABLE mk_diagnosis_confidence_policy IS '置信分级策略：权重→等级阈值，可按租户/科室 scope_key 覆盖，不硬编码';

-- 平台主租户默认置信策略种子（开箱可用；客户租户可新增 scope_key 覆盖，运行时未覆盖回退 t-1）
INSERT INTO mk_diagnosis_confidence_policy
    (tenant_id, scope_key, strong_min_major, require_all_required, moderate_min_hits, created_at, created_by, updated_at, updated_by)
VALUES ('t-1', 'DEFAULT', 2, 1, 1, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

-- 租户索引（迁移规约 migration.tenant-index：带 tenant_id 业务表须声明租户索引）
CREATE INDEX idx_mk_diagnosis_confpolicy_tenant ON mk_diagnosis_confidence_policy (tenant_id);
