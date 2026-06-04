-- MedKernel v1.0 GA · OPT-04 临床安全红线规则库 PR1（PostgreSQL）
-- ROLLBACK: 若需回退，先导出 mk_engine_clinical_redline 红线版本、危害分析和证据来源，再删除本表；已被推荐卡或审计引用的红线版本不得物理丢失证据。

CREATE TABLE IF NOT EXISTS mk_engine_clinical_redline (
    id                              BIGSERIAL PRIMARY KEY,
    redline_id                      VARCHAR(64)   NOT NULL,
    tenant_id                       VARCHAR(64)   NOT NULL,
    category                        VARCHAR(64)   NOT NULL,
    trigger_point                   VARCHAR(64)   NOT NULL,
    scope_type                      VARCHAR(32)   NOT NULL,
    scope_ref                       VARCHAR(128)  NOT NULL,
    active_scope_key                VARCHAR(512)  NULL,
    redline_key                     VARCHAR(128)  NOT NULL,
    redline_version                 VARCHAR(64)   NOT NULL,
    status                          VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    hazard_severity                 VARCHAR(32)   NOT NULL,
    risk_matrix_id                  VARCHAR(64)   NOT NULL,
    risk_matrix_version             VARCHAR(64)   NOT NULL,
    review_requirement              VARCHAR(64)   NOT NULL,
    silent_run_hours                INT           NOT NULL DEFAULT 0,
    release_gate                    VARCHAR(128)  NOT NULL,
    title                           VARCHAR(256)  NOT NULL,
    clinical_hazard                 VARCHAR(1024) NOT NULL,
    condition_dsl                   VARCHAR(4000) NOT NULL,
    evidence_source                 VARCHAR(512)  NOT NULL,
    evidence_reference              VARCHAR(512)  NOT NULL,
    source_version_id               BIGINT        NULL,
    lower_tenant_override_allowed   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by                      VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by                      VARCHAR(64)   NOT NULL DEFAULT 'system',
    trace_id                        VARCHAR(128)  NULL,
    CONSTRAINT uk_clinical_redline_id UNIQUE (redline_id),
    CONSTRAINT uk_clinical_redline_version UNIQUE (tenant_id, redline_key, redline_version),
    CONSTRAINT uk_clinical_redline_active_scope UNIQUE (tenant_id, active_scope_key),
    CONSTRAINT ck_clinical_redline_category CHECK (
        category IN ('DRUG_INTERACTION','CRITICAL_VALUE','DOSE_LIMIT','ANTIMICROBIAL_RESTRICTION','SPECIAL_POPULATION_CONTRAINDICATION')
    ),
    CONSTRAINT ck_clinical_redline_status CHECK (status IN ('DRAFT','SILENT_RUNNING','ACTIVE','WITHDRAWN')),
    CONSTRAINT ck_clinical_redline_hazard CHECK (hazard_severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_clinical_redline_review CHECK (
        review_requirement IN ('OPTIONAL_REVIEW','PHYSICIAN_CONFIRMATION','DUAL_REVIEW')
    ),
    CONSTRAINT ck_clinical_redline_override CHECK (lower_tenant_override_allowed IN (TRUE, FALSE))
);

CREATE INDEX IF NOT EXISTS idx_clinical_redline_active
    ON mk_engine_clinical_redline (tenant_id, status, category, trigger_point, updated_at);
CREATE INDEX IF NOT EXISTS idx_clinical_redline_category
    ON mk_engine_clinical_redline (tenant_id, category, status, redline_key);
CREATE INDEX IF NOT EXISTS idx_clinical_redline_source
    ON mk_engine_clinical_redline (tenant_id, source_version_id, status);

COMMENT ON TABLE mk_engine_clinical_redline IS '临床安全红线规则库：保存 DDI、危急值、剂量上限、抗菌限制和特殊人群禁忌等红线版本';
COMMENT ON COLUMN mk_engine_clinical_redline.redline_id IS '红线版本业务 ID';
COMMENT ON COLUMN mk_engine_clinical_redline.tenant_id IS '租户 ID；平台统一红线同步到租户后仍按租户隔离读取';
COMMENT ON COLUMN mk_engine_clinical_redline.category IS '红线类目：DDI、危急值、剂量上限、抗菌限制、特殊人群禁忌';
COMMENT ON COLUMN mk_engine_clinical_redline.trigger_point IS '适用 CDS Hooks 触发点';
COMMENT ON COLUMN mk_engine_clinical_redline.scope_type IS '适用域类型：TENANT/ORG/SPECIALTY 等受控范围';
COMMENT ON COLUMN mk_engine_clinical_redline.scope_ref IS '适用域引用';
COMMENT ON COLUMN mk_engine_clinical_redline.active_scope_key IS 'ACTIVE 唯一作用域键；非 ACTIVE 为空以允许多版本并存';
COMMENT ON COLUMN mk_engine_clinical_redline.redline_key IS '红线稳定键，同一红线跨版本不变';
COMMENT ON COLUMN mk_engine_clinical_redline.redline_version IS '红线内容版本号';
COMMENT ON COLUMN mk_engine_clinical_redline.status IS '红线状态：草稿、静默试运行、生效、撤回';
COMMENT ON COLUMN mk_engine_clinical_redline.hazard_severity IS '危害严重度，绑定 OPT-03 风险矩阵输入';
COMMENT ON COLUMN mk_engine_clinical_redline.risk_matrix_id IS '绑定的 OPT-03 风险矩阵规则 ID';
COMMENT ON COLUMN mk_engine_clinical_redline.risk_matrix_version IS '绑定的 OPT-03 风险矩阵版本';
COMMENT ON COLUMN mk_engine_clinical_redline.review_requirement IS '红线命中后的人工审核要求';
COMMENT ON COLUMN mk_engine_clinical_redline.silent_run_hours IS '上线前静默试运行小时数要求';
COMMENT ON COLUMN mk_engine_clinical_redline.release_gate IS '上线门槛编码';
COMMENT ON COLUMN mk_engine_clinical_redline.title IS '红线中文标题';
COMMENT ON COLUMN mk_engine_clinical_redline.clinical_hazard IS '危害分析说明';
COMMENT ON COLUMN mk_engine_clinical_redline.condition_dsl IS '来自配置或知识资产的规则条件 DSL；不得在代码写死医学常量';
COMMENT ON COLUMN mk_engine_clinical_redline.evidence_source IS '证据来源说明';
COMMENT ON COLUMN mk_engine_clinical_redline.evidence_reference IS '证据引用锚点';
COMMENT ON COLUMN mk_engine_clinical_redline.source_version_id IS '关联知识来源版本 ID';
COMMENT ON COLUMN mk_engine_clinical_redline.lower_tenant_override_allowed IS '下级租户是否允许关闭；安全红线默认 FALSE';
COMMENT ON COLUMN mk_engine_clinical_redline.trace_id IS '创建或更新追踪 ID';
