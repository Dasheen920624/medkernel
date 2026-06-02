-- MedKernel v1.0 GA · SYS-04 组织继承覆盖解释（人大金仓）
-- ROLLBACK：确认无继承覆盖解释依赖后，删除 mk_version_inheritance_override 表，并移除 mk_version_asset_version.safety_policy。

ALTER TABLE mk_version_asset_version
    ADD COLUMN IF NOT EXISTS safety_policy VARCHAR(32) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE mk_version_asset_version
    ADD CONSTRAINT ck_mk_version_asset_safety_policy
    CHECK (safety_policy IN ('NORMAL','SAFETY_REDLINE'));

CREATE TABLE IF NOT EXISTS mk_version_inheritance_override (
    id                   BIGSERIAL PRIMARY KEY,
    override_id          VARCHAR(64)   NOT NULL,
    tenant_id            VARCHAR(64)   NOT NULL,
    asset_type           VARCHAR(32)   NOT NULL,
    asset_identity       VARCHAR(128)  NOT NULL,
    inherited_version_id VARCHAR(64)   NOT NULL,
    override_version_id  VARCHAR(64)   NULL,
    override_mode        VARCHAR(32)   NOT NULL,
    org_path             VARCHAR(256)  NOT NULL,
    applicable_scope     VARCHAR(256)  NOT NULL,
    diff_summary         VARCHAR(1024) NOT NULL,
    override_reason      VARCHAR(1024) NOT NULL,
    impact_scope         VARCHAR(1024) NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(64)   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by           VARCHAR(64)   NOT NULL,
    trace_id             VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_version_inheritance_override_id UNIQUE (override_id),
    CONSTRAINT uk_mk_version_inheritance_override_active
        UNIQUE (tenant_id, asset_type, asset_identity, org_path, applicable_scope),
    CONSTRAINT ck_mk_version_inheritance_override_type CHECK (asset_type IN
        ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','PACKAGE','EVALUATION')),
    CONSTRAINT ck_mk_version_inheritance_override_mode CHECK (override_mode IN ('REPLACE','DISABLE'))
);

CREATE INDEX IF NOT EXISTS idx_mk_version_inheritance_override_scope
    ON mk_version_inheritance_override (tenant_id, org_path, asset_type, asset_identity);
CREATE INDEX IF NOT EXISTS idx_mk_version_inheritance_override_version
    ON mk_version_inheritance_override (tenant_id, inherited_version_id, override_version_id);

COMMENT ON COLUMN mk_version_asset_version.safety_policy IS '继承安全策略：NORMAL 普通配置 / SAFETY_REDLINE 高风险禁忌红线';
COMMENT ON TABLE mk_version_inheritance_override IS '组织继承局部覆盖解释：记录下级覆盖上级版本的差异、原因和影响范围';
COMMENT ON COLUMN mk_version_inheritance_override.override_id IS '覆盖解释业务 ID，跨方言唯一';
COMMENT ON COLUMN mk_version_inheritance_override.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_version_inheritance_override.asset_type IS '资产类型：知识、术语、规则、路径、包或评估指标';
COMMENT ON COLUMN mk_version_inheritance_override.asset_identity IS '资产身份编码';
COMMENT ON COLUMN mk_version_inheritance_override.inherited_version_id IS '被继承的上级资产版本 ID';
COMMENT ON COLUMN mk_version_inheritance_override.override_version_id IS '本地覆盖版本 ID；关闭继承时为空';
COMMENT ON COLUMN mk_version_inheritance_override.override_mode IS '覆盖方式：REPLACE 本地版本替换 / DISABLE 关闭继承';
COMMENT ON COLUMN mk_version_inheritance_override.org_path IS '覆盖生效组织路径';
COMMENT ON COLUMN mk_version_inheritance_override.applicable_scope IS '适用人群或上下文范围';
COMMENT ON COLUMN mk_version_inheritance_override.diff_summary IS '本地覆盖与继承版本的差异说明';
COMMENT ON COLUMN mk_version_inheritance_override.override_reason IS '本地覆盖的业务原因和审核依据';
COMMENT ON COLUMN mk_version_inheritance_override.impact_scope IS '覆盖影响范围说明';
COMMENT ON COLUMN mk_version_inheritance_override.created_at IS '创建时间';
COMMENT ON COLUMN mk_version_inheritance_override.created_by IS '创建人';
COMMENT ON COLUMN mk_version_inheritance_override.updated_at IS '更新时间';
COMMENT ON COLUMN mk_version_inheritance_override.updated_by IS '更新人';
COMMENT ON COLUMN mk_version_inheritance_override.trace_id IS '链路追踪 ID';
