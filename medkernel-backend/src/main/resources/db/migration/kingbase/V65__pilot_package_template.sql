-- MedKernel v1.0 GA · SVC-PILOT-03 试点首发配置包模板（人大金仓 Kingbase）

CREATE TABLE IF NOT EXISTS mk_pkg_pilot_package_template (
    template_id             VARCHAR(64)   PRIMARY KEY,
    tenant_id               VARCHAR(64)   NOT NULL,
    template_code           VARCHAR(128)  NOT NULL,
    name                    VARCHAR(256)  NOT NULL,
    description             VARCHAR(1000) NULL,
    package_code_prefix     VARCHAR(128)  NOT NULL,
    default_package_version VARCHAR(64)   NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    created_at              TIMESTAMP     NOT NULL,
    created_by              VARCHAR(64)   NOT NULL,
    updated_at              TIMESTAMP     NOT NULL,
    updated_by              VARCHAR(64)   NOT NULL,
    trace_id                VARCHAR(128)  NULL,
    CONSTRAINT uk_pkg_tpl_tenant_code UNIQUE (tenant_id, template_code),
    CONSTRAINT ck_pkg_tpl_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_pkg_tpl_tenant_status
    ON mk_pkg_pilot_package_template (tenant_id, status, template_code);

CREATE TABLE IF NOT EXISTS mk_pkg_pilot_template_item (
    item_id          VARCHAR(64)   PRIMARY KEY,
    tenant_id        VARCHAR(64)   NOT NULL,
    template_id      VARCHAR(64)   NOT NULL,
    asset_type       VARCHAR(32)   NOT NULL,
    asset_id         VARCHAR(128)  NOT NULL,
    asset_version    VARCHAR(64)   NOT NULL,
    required_flag    BOOLEAN       NOT NULL,
    sort_order       INTEGER       NOT NULL,
    dependency_note  VARCHAR(512)  NULL,
    created_at       TIMESTAMP     NOT NULL,
    created_by       VARCHAR(64)   NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,
    updated_by       VARCHAR(64)   NOT NULL,
    trace_id         VARCHAR(128)  NULL,
    CONSTRAINT fk_pkg_tpli_template FOREIGN KEY (template_id)
        REFERENCES mk_pkg_pilot_package_template (template_id),
    CONSTRAINT uk_pkg_tpli_asset UNIQUE (tenant_id, template_id, asset_type, asset_id, asset_version),
    CONSTRAINT ck_pkg_tpli_type CHECK (asset_type IN ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','EVALUATION','FOLLOWUP')),
    CONSTRAINT ck_pkg_tpli_required CHECK (required_flag IN (TRUE,FALSE)),
    CONSTRAINT ck_pkg_tpli_sort CHECK (sort_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pkg_tpli_template
    ON mk_pkg_pilot_template_item (tenant_id, template_id, sort_order);

COMMENT ON TABLE mk_pkg_pilot_package_template IS '试点首发配置包模板';
COMMENT ON COLUMN mk_pkg_pilot_package_template.template_id IS '模板业务主键（ULID 形态）';
COMMENT ON COLUMN mk_pkg_pilot_package_template.tenant_id IS '模板归属租户 ID，平台模板使用 t-1';
COMMENT ON COLUMN mk_pkg_pilot_package_template.template_code IS '模板稳定编码';
COMMENT ON COLUMN mk_pkg_pilot_package_template.name IS '模板中文名称';
COMMENT ON COLUMN mk_pkg_pilot_package_template.description IS '模板说明';
COMMENT ON COLUMN mk_pkg_pilot_package_template.package_code_prefix IS '默认配置包编码前缀';
COMMENT ON COLUMN mk_pkg_pilot_package_template.default_package_version IS '默认配置包版本';
COMMENT ON COLUMN mk_pkg_pilot_package_template.status IS '模板状态：ACTIVE/INACTIVE';
COMMENT ON COLUMN mk_pkg_pilot_package_template.created_at IS '创建时间';
COMMENT ON COLUMN mk_pkg_pilot_package_template.created_by IS '创建人';
COMMENT ON COLUMN mk_pkg_pilot_package_template.updated_at IS '更新时间';
COMMENT ON COLUMN mk_pkg_pilot_package_template.updated_by IS '更新人';
COMMENT ON COLUMN mk_pkg_pilot_package_template.trace_id IS '链路追踪 ID';

COMMENT ON TABLE mk_pkg_pilot_template_item IS '试点首发配置包模板资产项';
COMMENT ON COLUMN mk_pkg_pilot_template_item.item_id IS '模板资产项业务主键（ULID 形态）';
COMMENT ON COLUMN mk_pkg_pilot_template_item.tenant_id IS '模板资产项归属租户 ID';
COMMENT ON COLUMN mk_pkg_pilot_template_item.template_id IS '所属模板业务主键';
COMMENT ON COLUMN mk_pkg_pilot_template_item.asset_type IS '资产类型：知识/术语/规则/路径/指标/随访';
COMMENT ON COLUMN mk_pkg_pilot_template_item.asset_id IS '资产稳定业务 ID';
COMMENT ON COLUMN mk_pkg_pilot_template_item.asset_version IS '资产版本号';
COMMENT ON COLUMN mk_pkg_pilot_template_item.required_flag IS '是否必需资产项';
COMMENT ON COLUMN mk_pkg_pilot_template_item.sort_order IS '模板内展示与引用顺序';
COMMENT ON COLUMN mk_pkg_pilot_template_item.dependency_note IS '依赖说明';
COMMENT ON COLUMN mk_pkg_pilot_template_item.created_at IS '创建时间';
COMMENT ON COLUMN mk_pkg_pilot_template_item.created_by IS '创建人';
COMMENT ON COLUMN mk_pkg_pilot_template_item.updated_at IS '更新时间';
COMMENT ON COLUMN mk_pkg_pilot_template_item.updated_by IS '更新人';
COMMENT ON COLUMN mk_pkg_pilot_template_item.trace_id IS '链路追踪 ID';
