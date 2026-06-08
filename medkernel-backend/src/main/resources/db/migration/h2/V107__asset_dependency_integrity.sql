-- MedKernel v1.0 GA · 资产依赖与引用完整性（H2）
-- ROLLBACK：确认无发布校验与协同解析依赖后，删除 mk_version_asset_dependency。

CREATE TABLE IF NOT EXISTS mk_version_asset_dependency (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dependency_id         VARCHAR(64)  NOT NULL,
    tenant_id             VARCHAR(64)  NOT NULL,
    asset_type            VARCHAR(32)  NOT NULL,
    asset_identity        VARCHAR(128) NOT NULL,
    version_id            VARCHAR(64)  NOT NULL,
    depends_on_asset_type VARCHAR(32)  NOT NULL,
    depends_on_identity   VARCHAR(128) NOT NULL,
    min_version_no        VARCHAR(64)  NULL,
    max_version_no        VARCHAR(64)  NULL,
    dependency_kind       VARCHAR(32)  NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(64)  NOT NULL,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(64)  NOT NULL,
    trace_id              VARCHAR(128) NULL,
    CONSTRAINT uk_mk_version_asset_dependency_id UNIQUE (dependency_id),
    CONSTRAINT uk_mk_version_asset_dependency_edge UNIQUE (
        tenant_id, asset_type, asset_identity, version_id, depends_on_asset_type, depends_on_identity
    ),
    CONSTRAINT ck_mk_version_asset_dependency_owner_type CHECK (asset_type IN (
        'KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE',
        'RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET',
        'ACTION_CARD','SUBPATHWAY'
    )),
    CONSTRAINT ck_mk_version_asset_dependency_target_type CHECK (depends_on_asset_type IN (
        'KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP','FIELD_CATALOG','PACKAGE',
        'RECOMMENDATION','SAFETY','CDSS_RISK','CONDITION_FRAGMENT','VALUE_SET','FORMULA','ORDER_SET',
        'ACTION_CARD','SUBPATHWAY'
    )),
    CONSTRAINT ck_mk_version_asset_dependency_kind CHECK (dependency_kind IN (
        'FIELD','TERMINOLOGY','RULE','PATHWAY','PACKAGE_ITEM','EVALUATION','FOLLOWUP','OTHER'
    ))
);

CREATE INDEX IF NOT EXISTS idx_mk_version_asset_dependency_owner
    ON mk_version_asset_dependency (tenant_id, asset_type, asset_identity, version_id);
CREATE INDEX IF NOT EXISTS idx_mk_version_asset_dependency_target
    ON mk_version_asset_dependency (tenant_id, depends_on_asset_type, depends_on_identity);

COMMENT ON TABLE mk_version_asset_dependency IS '资产依赖图：记录某一资产版本引用的字段、字典、规则、路径或包项';
COMMENT ON COLUMN mk_version_asset_dependency.dependency_id IS '依赖边业务 ID，跨方言唯一';
COMMENT ON COLUMN mk_version_asset_dependency.tenant_id IS '租户 ID；平台资产使用 __platform__';
COMMENT ON COLUMN mk_version_asset_dependency.asset_type IS '依赖来源资产类型';
COMMENT ON COLUMN mk_version_asset_dependency.asset_identity IS '依赖来源资产身份';
COMMENT ON COLUMN mk_version_asset_dependency.version_id IS '依赖来源资产版本 ID';
COMMENT ON COLUMN mk_version_asset_dependency.depends_on_asset_type IS '被依赖资产类型';
COMMENT ON COLUMN mk_version_asset_dependency.depends_on_identity IS '被依赖资产身份；发布与 DISABLE 校验以此定位悬空引用';
COMMENT ON COLUMN mk_version_asset_dependency.min_version_no IS '被依赖资产最小兼容版本号；为空表示不限制下界';
COMMENT ON COLUMN mk_version_asset_dependency.max_version_no IS '被依赖资产最大兼容版本号；为空表示不限制上界';
COMMENT ON COLUMN mk_version_asset_dependency.dependency_kind IS '依赖边类型：字段、术语、规则、路径、包项、评估、随访或其他';
COMMENT ON COLUMN mk_version_asset_dependency.trace_id IS '链路追踪 ID';
