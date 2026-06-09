-- MedKernel v1.0 GA · 发布模拟、灰度放量与覆盖复用（PostgreSQL）
-- ROLLBACK：删除四张新增表，移除发布计划灰度列，并恢复发布状态约束。

ALTER TABLE mk_version_release_plan ADD COLUMN IF NOT EXISTS rollout_strategy VARCHAR(32) DEFAULT 'ALL' NOT NULL;
ALTER TABLE mk_version_release_plan ADD COLUMN IF NOT EXISTS rollout_config_json TEXT NULL;
ALTER TABLE mk_version_release_plan ADD COLUMN IF NOT EXISTS rollout_stage_index INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE mk_version_release_plan ADD COLUMN IF NOT EXISTS rollout_paused_reason VARCHAR(1000) NULL;
ALTER TABLE mk_version_release_plan ADD CONSTRAINT ck_mk_version_release_rollout_strategy
    CHECK (rollout_strategy IN ('ALL','ORG_SUBTREE','ORG_LIST','CANARY_BED_PERCENT','STAGED'));
ALTER TABLE mk_version_release_plan DROP CONSTRAINT IF EXISTS ck_mk_version_release_plan_status;
ALTER TABLE mk_version_release_plan ADD CONSTRAINT ck_mk_version_release_plan_status
    CHECK (status IN ('IN_REVIEW','REJECTED','APPROVED','PUBLISHED','GRAY','PAUSED','ROLLED_BACK','FAILED'));
ALTER TABLE mk_engine_notification DROP CONSTRAINT IF EXISTS ck_notification_source_type;
ALTER TABLE mk_engine_notification ADD CONSTRAINT ck_notification_source_type
    CHECK (source_type IN ('FOLLOWUP_EVENT','SAFETY_REVIEW','WORKFLOW_TODO','SYNC_EVENT',
        'RULE_EVENT','PATHWAY_EVENT','RELEASE_ROLLOUT'));

CREATE TABLE IF NOT EXISTS mk_version_rollout_observation (
    id BIGSERIAL PRIMARY KEY,
    observation_id VARCHAR(64) NOT NULL,
    plan_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    stage_index INTEGER NOT NULL,
    sample_count BIGINT NOT NULL,
    hit_count BIGINT NOT NULL,
    block_count BIGINT NOT NULL,
    manual_rejection_count BIGINT NOT NULL,
    anomaly_count BIGINT NOT NULL,
    hit_rate NUMERIC(9,6) NOT NULL,
    block_rate NUMERIC(9,6) NOT NULL,
    manual_rejection_rate NUMERIC(9,6) NOT NULL,
    anomaly_rate NUMERIC(9,6) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NULL,
    CONSTRAINT uk_mk_version_rollout_observation_id UNIQUE (observation_id),
    CONSTRAINT ck_mk_version_rollout_observation_counts CHECK (
        stage_index >= 0 AND sample_count > 0 AND hit_count >= 0 AND block_count >= 0
        AND manual_rejection_count >= 0 AND anomaly_count >= 0),
    CONSTRAINT ck_mk_version_rollout_observation_rates CHECK (
        hit_rate BETWEEN 0 AND 1 AND block_rate BETWEEN 0 AND 1
        AND manual_rejection_rate BETWEEN 0 AND 1 AND anomaly_rate BETWEEN 0 AND 1)
);
CREATE INDEX IF NOT EXISTS idx_mk_version_rollout_plan
    ON mk_version_rollout_observation (tenant_id, plan_id, stage_index, observed_at);

CREATE TABLE IF NOT EXISTS mk_version_override_template (
    id BIGSERIAL PRIMARY KEY,
    template_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    applicable_scope VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NULL,
    CONSTRAINT uk_mk_version_override_template_id UNIQUE (template_id),
    CONSTRAINT uk_mk_version_override_template_name UNIQUE (tenant_id, template_name),
    CONSTRAINT ck_mk_version_override_template_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_mk_version_override_template_tenant
    ON mk_version_override_template (tenant_id, status, updated_at);

CREATE TABLE IF NOT EXISTS mk_version_override_template_item (
    id BIGSERIAL PRIMARY KEY,
    item_id VARCHAR(64) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    asset_identity VARCHAR(200) NOT NULL,
    inherited_version_id VARCHAR(64) NULL,
    source_override_version_id VARCHAR(64) NULL,
    override_mode VARCHAR(16) NOT NULL,
    propagation VARCHAR(16) NOT NULL,
    applicable_scope VARCHAR(1000) NOT NULL,
    diff_summary VARCHAR(2000) NOT NULL,
    override_reason VARCHAR(1000) NOT NULL,
    CONSTRAINT uk_mk_version_override_template_item_id UNIQUE (item_id),
    CONSTRAINT uk_mk_version_override_template_asset UNIQUE (template_id, asset_type, asset_identity),
    CONSTRAINT ck_mk_version_override_template_mode CHECK (override_mode IN ('REPLACE','DISABLE','ADD')),
    CONSTRAINT ck_mk_version_override_template_propagation CHECK (propagation IN ('INHERITABLE','EXCLUSIVE'))
);
CREATE INDEX IF NOT EXISTS idx_mk_version_override_template_item
    ON mk_version_override_template_item (template_id, asset_type, asset_identity);

CREATE TABLE IF NOT EXISTS mk_version_override_operation (
    id BIGSERIAL PRIMARY KEY,
    operation_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    template_id VARCHAR(64) NULL,
    source_org_unit_id VARCHAR(64) NULL,
    target_org_units_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    preview_digest VARCHAR(64) NOT NULL,
    result_summary_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NULL,
    CONSTRAINT uk_mk_version_override_operation_id UNIQUE (operation_id),
    CONSTRAINT ck_mk_version_override_operation_type CHECK (operation_type IN ('APPLY','REVOKE','CLONE')),
    CONSTRAINT ck_mk_version_override_operation_status CHECK (status IN ('PREVIEWED','APPLIED','REVOKED','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_mk_version_override_operation_tenant
    ON mk_version_override_operation (tenant_id, operation_type, created_at);

COMMENT ON COLUMN mk_version_release_plan.rollout_strategy IS '发布放量策略，与组织作用域独立';
COMMENT ON COLUMN mk_version_release_plan.rollout_config_json IS '结构化灰度策略参数 JSON';
COMMENT ON COLUMN mk_version_release_plan.rollout_stage_index IS '当前已进入的灰度批次下标';
COMMENT ON COLUMN mk_version_release_plan.rollout_paused_reason IS '自动或人工暂停放量的原因';
COMMENT ON TABLE mk_version_rollout_observation IS '灰度批次关键指标观测事实';
COMMENT ON TABLE mk_version_override_template IS '可复用的组织覆盖模板';
COMMENT ON TABLE mk_version_override_template_item IS '覆盖模板内的资产覆盖项';
COMMENT ON TABLE mk_version_override_operation IS '覆盖模板批量预演、生效、撤销和克隆记录';
