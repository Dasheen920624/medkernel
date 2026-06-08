-- MedKernel v1.0 GA · GA-ENG-API-10 包发布 API（Oracle）

CREATE TABLE knowledge_package (
    id                NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    package_id        VARCHAR2(64 CHAR)   NOT NULL,
    tenant_id         VARCHAR2(64 CHAR)   NOT NULL,
    package_code      VARCHAR2(128 CHAR)  NOT NULL,
    package_version   VARCHAR2(64 CHAR)   NOT NULL,
    name              VARCHAR2(256 CHAR)  NOT NULL,
    description       CLOB,
    status            VARCHAR2(32 CHAR)   DEFAULT 'DRAFT' NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64 CHAR)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64 CHAR)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128 CHAR),
    CONSTRAINT uk_knowledge_package_id UNIQUE (tenant_id, package_id),
    CONSTRAINT uk_knowledge_package_tenant_version UNIQUE (tenant_id, package_code, package_version),
    CONSTRAINT ck_knowledge_package_status CHECK (status IN (
        'DRAFT','PENDING_REVIEW','PUBLISHED','ACTIVE','OFFLINE','ARCHIVED'
    ))
);

CREATE INDEX idx_knowledge_pkg_tenant_status ON knowledge_package (tenant_id, status, updated_at);

CREATE TABLE package_item (
    id                NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id           VARCHAR2(64 CHAR)   NOT NULL,
    tenant_id         VARCHAR2(64 CHAR)   NOT NULL,
    package_id        VARCHAR2(64 CHAR)   NOT NULL,
    asset_type        VARCHAR2(32 CHAR)   NOT NULL,
    asset_id          VARCHAR2(64 CHAR)   NOT NULL,
    asset_version     VARCHAR2(64 CHAR)   NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64 CHAR)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64 CHAR)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128 CHAR),
    CONSTRAINT uk_package_item_id UNIQUE (tenant_id, item_id),
    CONSTRAINT uk_package_item_tenant_asset UNIQUE (tenant_id, package_id, asset_type, asset_id),
    CONSTRAINT ck_package_item_asset_type CHECK (asset_type IN (
        'KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP'
    ))
);

CREATE INDEX idx_package_item_pkg ON package_item (tenant_id, package_id, asset_type);

CREATE TABLE release_plan (
    id                NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id           VARCHAR2(64 CHAR)   NOT NULL,
    tenant_id         VARCHAR2(64 CHAR)   NOT NULL,
    package_id        VARCHAR2(64 CHAR)   NOT NULL,
    target_org_unit_id VARCHAR2(64 CHAR)  NOT NULL,
    strategy          VARCHAR2(32 CHAR)   NOT NULL,
    scope_type        VARCHAR2(32 CHAR)   NOT NULL,
    scope_value       VARCHAR2(256 CHAR),
    status            VARCHAR2(32 CHAR)   DEFAULT 'DRAFT' NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64 CHAR)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64 CHAR)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128 CHAR),
    CONSTRAINT uk_release_plan_id UNIQUE (tenant_id, plan_id),
    CONSTRAINT ck_release_plan_strategy CHECK (strategy IN ('GRAYSCALE','FULL')),
    CONSTRAINT ck_release_plan_scope_type CHECK (scope_type IN ('ALL','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT')),
    CONSTRAINT ck_release_plan_status CHECK (status IN ('DRAFT','EXECUTING','SUCCESS','FAILED','ROLLBACKED'))
);

CREATE INDEX idx_release_plan_pkg ON release_plan (tenant_id, package_id, status);

CREATE TABLE sync_log (
    id                NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    log_id            VARCHAR2(64 CHAR)   NOT NULL,
    tenant_id         VARCHAR2(64 CHAR)   NOT NULL,
    plan_id           VARCHAR2(64 CHAR)   NOT NULL,
    adapter_id         VARCHAR2(64 CHAR)   NOT NULL,
    status            VARCHAR2(32 CHAR)   DEFAULT 'RUNNING' NOT NULL,
    error_code        VARCHAR2(64 CHAR),
    error_message     CLOB,
    retry_count       NUMBER(10)          DEFAULT 0 NOT NULL,
    sync_evidence     CLOB,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by        VARCHAR2(64 CHAR)   DEFAULT 'system' NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by        VARCHAR2(64 CHAR)   DEFAULT 'system' NOT NULL,
    trace_id          VARCHAR2(128 CHAR),
    CONSTRAINT uk_sync_log_id UNIQUE (tenant_id, log_id),
    CONSTRAINT ck_sync_log_status CHECK (status IN ('RUNNING','SUCCESS','FAILED','RETRYING'))
);

CREATE INDEX idx_sync_log_plan ON sync_log (tenant_id, plan_id, status);

-- ===== 表与关键列中文注释（GA-ENG-API-10）=====

COMMENT ON TABLE knowledge_package IS '知识包主数据：保存已审核的字典、规则和路径等资产包的版本、状态与发布信息';
COMMENT ON COLUMN knowledge_package.package_id IS '知识包 ID（租户内业务主键）';
COMMENT ON COLUMN knowledge_package.tenant_id IS '租户 ID';
COMMENT ON COLUMN knowledge_package.package_code IS '知识包编码（租户内唯一）';
COMMENT ON COLUMN knowledge_package.package_version IS '知识包版本号（同编码下递增）';
COMMENT ON COLUMN knowledge_package.status IS '包生命周期状态：DRAFT 草稿 / PENDING_REVIEW 待审核 / PUBLISHED 已发布 / ACTIVE 激活 / OFFLINE 下线 / ARCHIVED 归档';

COMMENT ON TABLE package_item IS '包内资产条目：锁定包包含的具体资产、类型及其确切版本';
COMMENT ON COLUMN package_item.item_id IS '条目 ID';
COMMENT ON COLUMN package_item.package_id IS '关联的知识包 ID → knowledge_package.package_id';
COMMENT ON COLUMN package_item.asset_type IS '资产类型：KNOWLEDGE 知识 / TERMINOLOGY 字典 / RULE 规则 / PATHWAY 路径 / EVALUATION 指标 / FOLLOWUP 随访';
COMMENT ON COLUMN package_item.asset_id IS '关联的具体资产业务 ID';
COMMENT ON COLUMN package_item.asset_version IS '锁定的具体资产版本号';

COMMENT ON TABLE release_plan IS '发布与灰度计划：记录灰度与全量发布目标、策略及发布状态机';
COMMENT ON COLUMN release_plan.plan_id IS '发布计划 ID';
COMMENT ON COLUMN release_plan.package_id IS '关联的知识包 ID → knowledge_package.package_id';
COMMENT ON COLUMN release_plan.target_org_unit_id IS '发布的受众组织 ID';
COMMENT ON COLUMN release_plan.strategy IS '发布策略：GRAYSCALE 灰度 / FULL 全量';
COMMENT ON COLUMN release_plan.scope_type IS '组织作用范围类型：ALL 全量 / GROUP 集团 / HOSPITAL 医院 / CAMPUS 院区 / SITE 服务点 / DEPARTMENT 科室；专病由 specialty_id 横切表达';
COMMENT ON COLUMN release_plan.scope_value IS '作用范围值快照（ID逗号分隔）';

COMMENT ON TABLE sync_log IS '同步执行日志：保存投影执行状态、错误码、重试与加密签名存证';
COMMENT ON COLUMN sync_log.log_id IS '日志 ID';
COMMENT ON COLUMN sync_log.plan_id IS '关联的发布计划 ID';
COMMENT ON COLUMN sync_log.adapter_id IS '关联的统一集成适配器 ID';
COMMENT ON COLUMN sync_log.sync_evidence IS '投影执行数字签名与内容摘要存证';
