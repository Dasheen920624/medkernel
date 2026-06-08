-- MedKernel v1.0 GA · P12-6 统一资产库元数据（达梦）
-- ROLLBACK：如需回滚，先导出 mk_engine_authoring_asset_profile 与 mk_engine_authoring_asset_favorite，再删除两表并恢复资产类型 CHECK。

CREATE TABLE mk_engine_authoring_asset_profile (
    id                  NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    asset_type          VARCHAR(32)   NOT NULL,
    asset_id            VARCHAR(128)  NOT NULL,
    category            VARCHAR(64)   NULL,
    tags_json           CLOB          NULL,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by          VARCHAR(64)   DEFAULT 'system' NOT NULL,
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by          VARCHAR(64)   DEFAULT 'system' NOT NULL,
    trace_id            VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_engine_authoring_asset_profile_asset UNIQUE (tenant_id, asset_type, asset_id)
);

CREATE TABLE mk_engine_authoring_asset_favorite (
    id                  NUMBER(19)    IDENTITY PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    user_id             VARCHAR(64)   NOT NULL,
    asset_type          VARCHAR(32)   NOT NULL,
    asset_id            VARCHAR(128)  NOT NULL,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    trace_id            VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_engine_authoring_asset_favorite_user_asset UNIQUE (tenant_id, user_id, asset_type, asset_id)
);

CREATE INDEX idx_mk_engine_authoring_asset_profile_category
    ON mk_engine_authoring_asset_profile (tenant_id, category);

CREATE INDEX idx_mk_engine_authoring_asset_favorite_user
    ON mk_engine_authoring_asset_favorite (tenant_id, user_id);

CREATE INDEX idx_mk_engine_authoring_asset_favorite_asset
    ON mk_engine_authoring_asset_favorite (tenant_id, asset_type, asset_id);

ALTER TABLE mk_version_asset_version DROP CONSTRAINT ck_mk_version_asset_version_type;
ALTER TABLE mk_version_asset_version ADD CONSTRAINT ck_mk_version_asset_version_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

ALTER TABLE package_item DROP CONSTRAINT ck_package_item_asset_type;
ALTER TABLE package_item ADD CONSTRAINT ck_package_item_asset_type
    CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE','RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','ORDER_SET','ACTION_CARD','SUBPATHWAY'));

COMMENT ON TABLE mk_engine_authoring_asset_profile IS '统一创作资产库元数据表：保存跨规则、路径、条件片段、值集、医嘱集、动作卡和子路径的分类与标签';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.asset_type IS '资产类型：规则、路径、条件片段、值集、医嘱集、动作卡或子路径等统一枚举';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.asset_id IS '资产业务 ID';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.category IS '资产库分类';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.tags_json IS '资产标签 JSON 数组';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.created_at IS '创建时间';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.created_by IS '创建人';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.updated_at IS '更新时间';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.updated_by IS '更新人';
COMMENT ON COLUMN mk_engine_authoring_asset_profile.trace_id IS '请求链路追踪 ID';

COMMENT ON TABLE mk_engine_authoring_asset_favorite IS '统一创作资产库个人收藏表';
COMMENT ON COLUMN mk_engine_authoring_asset_favorite.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_engine_authoring_asset_favorite.user_id IS '用户 ID';
COMMENT ON COLUMN mk_engine_authoring_asset_favorite.asset_type IS '资产类型统一枚举';
COMMENT ON COLUMN mk_engine_authoring_asset_favorite.asset_id IS '资产业务 ID';
COMMENT ON COLUMN mk_engine_authoring_asset_favorite.created_at IS '收藏时间';
COMMENT ON COLUMN mk_engine_authoring_asset_favorite.trace_id IS '请求链路追踪 ID';

COMMENT ON COLUMN mk_version_asset_version.asset_type IS '资产类型（统一枚举）：知识、术语、规则、路径、评估、随访、字段目录、包、推荐、安全、CDSS风险、条件片段、值集、医嘱集、动作卡、子路径';
COMMENT ON COLUMN package_item.asset_type IS '包内资产类型（统一枚举）：知识、术语、规则、路径、评估、随访、字段目录、包、推荐、安全、CDSS风险、条件片段、值集、医嘱集、动作卡、子路径';
