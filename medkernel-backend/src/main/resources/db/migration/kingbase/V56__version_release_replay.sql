-- MedKernel v1.0 GA · SYS-04 发布流、激活事务与历史重放（人大金仓）
-- ROLLBACK：确认无发布证据与历史重放依赖后，删除 mk_version_release_plan、mk_version_activation_transaction、mk_version_replay_binding，并将版本状态约束恢复到不含 WITHDRAWN。

ALTER TABLE mk_version_asset_version DROP CONSTRAINT IF EXISTS ck_mk_version_asset_version_status;
ALTER TABLE mk_version_asset_version
    ADD CONSTRAINT ck_mk_version_asset_version_status CHECK (status IN
        ('DRAFT','PENDING_REVIEW','PUBLISHED','ACTIVE','OFFLINE','WITHDRAWN','ARCHIVED'));

CREATE TABLE IF NOT EXISTS mk_version_release_plan (
    id                 BIGSERIAL PRIMARY KEY,
    plan_id            VARCHAR(64)   NOT NULL,
    tenant_id          VARCHAR(64)   NOT NULL,
    asset_type         VARCHAR(32)   NOT NULL,
    asset_identity     VARCHAR(128)  NOT NULL,
    version_id         VARCHAR(64)   NOT NULL,
    from_version_id    VARCHAR(64)   NULL,
    target_org_path    VARCHAR(256)  NOT NULL,
    applicable_scope   VARCHAR(256)  NOT NULL,
    scope_type         VARCHAR(32)   NOT NULL,
    scope_value        VARCHAR(1024) NULL,
    status             VARCHAR(32)   NOT NULL,
    impact_digest      VARCHAR(1024) NOT NULL,
    review_conclusion  VARCHAR(1024) NULL,
    evidence_summary   VARCHAR(2048) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64)   NOT NULL,
    trace_id           VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_version_release_plan_id UNIQUE (plan_id),
    CONSTRAINT ck_mk_version_release_plan_type CHECK (asset_type IN
        ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','PACKAGE','EVALUATION')),
    CONSTRAINT ck_mk_version_release_plan_scope CHECK (scope_type IN
        ('ALL','GROUP','HOSPITAL','CAMPUS','SITE','DEPARTMENT','SPECIALTY','BED_PERCENT')),
    CONSTRAINT ck_mk_version_release_plan_status CHECK (status IN
        ('PENDING_REVIEW','SILENT_OBSERVATION','GRAY','FULL','ROLLBACKED','FAILED'))
);

CREATE TABLE IF NOT EXISTS mk_version_activation_transaction (
    id                 BIGSERIAL PRIMARY KEY,
    transaction_id     VARCHAR(64)   NOT NULL,
    tenant_id          VARCHAR(64)   NOT NULL,
    asset_type         VARCHAR(32)   NOT NULL,
    asset_identity     VARCHAR(128)  NOT NULL,
    from_version_id    VARCHAR(64)   NULL,
    to_version_id      VARCHAR(64)   NOT NULL,
    action             VARCHAR(32)   NOT NULL,
    active_scope_key   VARCHAR(512)  NOT NULL,
    impact_digest      VARCHAR(1024) NULL,
    evidence_summary   VARCHAR(2048) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64)   NOT NULL,
    trace_id           VARCHAR(128)  NULL,
    CONSTRAINT uk_mk_version_activation_transaction_id UNIQUE (transaction_id),
    CONSTRAINT uk_mk_version_activation_transaction_idem UNIQUE
        (tenant_id, asset_type, asset_identity, to_version_id, action, active_scope_key),
    CONSTRAINT ck_mk_version_activation_transaction_type CHECK (asset_type IN
        ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','PACKAGE','EVALUATION')),
    CONSTRAINT ck_mk_version_activation_transaction_action CHECK (action IN ('FULL_ACTIVATE','ROLLBACK'))
);

CREATE TABLE IF NOT EXISTS mk_version_replay_binding (
    id                  BIGSERIAL PRIMARY KEY,
    binding_id          VARCHAR(64)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    asset_type          VARCHAR(32)  NOT NULL,
    asset_identity      VARCHAR(128) NOT NULL,
    version_id          VARCHAR(64)  NOT NULL,
    patient_snapshot_id VARCHAR(128) NOT NULL,
    runtime_event_id    VARCHAR(128) NOT NULL,
    result_hash         VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(64)  NOT NULL,
    trace_id            VARCHAR(128) NULL,
    CONSTRAINT uk_mk_version_replay_binding_id UNIQUE (binding_id),
    CONSTRAINT uk_mk_version_replay_binding_event UNIQUE (tenant_id, asset_type, asset_identity, runtime_event_id),
    CONSTRAINT ck_mk_version_replay_binding_type CHECK (asset_type IN
        ('KNOWLEDGE','TERMINOLOGY','RULE','PATHWAY','PACKAGE','EVALUATION')),
    CONSTRAINT ck_mk_version_replay_binding_hash CHECK (result_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_mk_version_release_plan_asset
    ON mk_version_release_plan (tenant_id, asset_type, asset_identity, status);
CREATE INDEX IF NOT EXISTS idx_mk_version_release_plan_version
    ON mk_version_release_plan (tenant_id, version_id, status);
CREATE INDEX IF NOT EXISTS idx_mk_version_activation_transaction_asset
    ON mk_version_activation_transaction (tenant_id, asset_type, asset_identity, created_at);
CREATE INDEX IF NOT EXISTS idx_mk_version_replay_binding_version
    ON mk_version_replay_binding (tenant_id, version_id, runtime_event_id);

COMMENT ON COLUMN mk_version_asset_version.status IS '版本状态：DRAFT 草稿 / PENDING_REVIEW 待审核 / PUBLISHED 已发布 / ACTIVE 生效中 / OFFLINE 已下线 / WITHDRAWN 已撤回 / ARCHIVED 已归档';
COMMENT ON TABLE mk_version_release_plan IS '通用版本发布计划：记录审核、静默观察、灰度、全量和回滚证据';
COMMENT ON COLUMN mk_version_release_plan.plan_id IS '发布计划业务 ID';
COMMENT ON COLUMN mk_version_release_plan.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_version_release_plan.asset_type IS '资产类型';
COMMENT ON COLUMN mk_version_release_plan.asset_identity IS '资产身份编码';
COMMENT ON COLUMN mk_version_release_plan.version_id IS '目标版本 ID';
COMMENT ON COLUMN mk_version_release_plan.from_version_id IS '来源版本 ID，全量替换或回滚时记录';
COMMENT ON COLUMN mk_version_release_plan.target_org_path IS '发布目标组织路径';
COMMENT ON COLUMN mk_version_release_plan.applicable_scope IS '发布适用范围';
COMMENT ON COLUMN mk_version_release_plan.scope_type IS '发布范围类型，灰度默认 BED_PERCENT';
COMMENT ON COLUMN mk_version_release_plan.scope_value IS '发布范围取值，灰度时记录百分比或范围快照';
COMMENT ON COLUMN mk_version_release_plan.status IS '发布计划状态';
COMMENT ON COLUMN mk_version_release_plan.impact_digest IS '发布影响摘要';
COMMENT ON COLUMN mk_version_release_plan.review_conclusion IS '审核结论';
COMMENT ON COLUMN mk_version_release_plan.evidence_summary IS '发布证据摘要';
COMMENT ON COLUMN mk_version_release_plan.created_at IS '创建时间';
COMMENT ON COLUMN mk_version_release_plan.created_by IS '创建人';
COMMENT ON COLUMN mk_version_release_plan.updated_at IS '更新时间';
COMMENT ON COLUMN mk_version_release_plan.updated_by IS '更新人';
COMMENT ON COLUMN mk_version_release_plan.trace_id IS '链路追踪 ID';
COMMENT ON TABLE mk_version_activation_transaction IS '通用版本激活事务：记录全量激活与回滚的原子切换证据';
COMMENT ON COLUMN mk_version_activation_transaction.transaction_id IS '激活事务业务 ID';
COMMENT ON COLUMN mk_version_activation_transaction.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_version_activation_transaction.asset_type IS '资产类型';
COMMENT ON COLUMN mk_version_activation_transaction.asset_identity IS '资产身份编码';
COMMENT ON COLUMN mk_version_activation_transaction.from_version_id IS '被替换或回滚来源版本 ID';
COMMENT ON COLUMN mk_version_activation_transaction.to_version_id IS '激活目标版本 ID';
COMMENT ON COLUMN mk_version_activation_transaction.action IS '激活动作：FULL_ACTIVATE 全量激活 / ROLLBACK 回滚';
COMMENT ON COLUMN mk_version_activation_transaction.active_scope_key IS '唯一生效域键';
COMMENT ON COLUMN mk_version_activation_transaction.impact_digest IS '影响摘要';
COMMENT ON COLUMN mk_version_activation_transaction.evidence_summary IS '激活事务证据摘要';
COMMENT ON COLUMN mk_version_activation_transaction.created_at IS '创建时间';
COMMENT ON COLUMN mk_version_activation_transaction.created_by IS '创建人';
COMMENT ON COLUMN mk_version_activation_transaction.updated_at IS '更新时间';
COMMENT ON COLUMN mk_version_activation_transaction.updated_by IS '更新人';
COMMENT ON COLUMN mk_version_activation_transaction.trace_id IS '链路追踪 ID';
COMMENT ON TABLE mk_version_replay_binding IS '历史重放绑定：将运行结果绑定到当时患者快照与资产版本';
COMMENT ON COLUMN mk_version_replay_binding.binding_id IS '重放绑定业务 ID';
COMMENT ON COLUMN mk_version_replay_binding.tenant_id IS '租户 ID';
COMMENT ON COLUMN mk_version_replay_binding.asset_type IS '资产类型';
COMMENT ON COLUMN mk_version_replay_binding.asset_identity IS '资产身份编码';
COMMENT ON COLUMN mk_version_replay_binding.version_id IS '历史资产版本 ID';
COMMENT ON COLUMN mk_version_replay_binding.patient_snapshot_id IS '当时患者上下文快照 ID';
COMMENT ON COLUMN mk_version_replay_binding.runtime_event_id IS '运行事件 ID';
COMMENT ON COLUMN mk_version_replay_binding.result_hash IS '运行结果 SHA-256 摘要';
COMMENT ON COLUMN mk_version_replay_binding.created_at IS '创建时间';
COMMENT ON COLUMN mk_version_replay_binding.created_by IS '创建人';
COMMENT ON COLUMN mk_version_replay_binding.updated_at IS '更新时间';
COMMENT ON COLUMN mk_version_replay_binding.updated_by IS '更新人';
COMMENT ON COLUMN mk_version_replay_binding.trace_id IS '链路追踪 ID';
