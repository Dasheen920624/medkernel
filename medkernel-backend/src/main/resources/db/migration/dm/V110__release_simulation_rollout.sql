-- MedKernel v1.0 GA · 发布模拟、灰度放量与覆盖复用（达梦）
-- ROLLBACK：删除四张新增表，移除发布计划灰度列，并恢复发布状态约束。

ALTER TABLE mk_version_release_plan ADD rollout_strategy VARCHAR2(32) DEFAULT 'ALL' NOT NULL;
ALTER TABLE mk_version_release_plan ADD rollout_config_json CLOB NULL;
ALTER TABLE mk_version_release_plan ADD rollout_stage_index INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE mk_version_release_plan ADD rollout_paused_reason VARCHAR2(1000) NULL;
ALTER TABLE mk_version_release_plan ADD CONSTRAINT ck_mk_version_release_rollout_strategy
    CHECK (rollout_strategy IN ('ALL','ORG_SUBTREE','ORG_LIST','CANARY_BED_PERCENT','STAGED'));
ALTER TABLE mk_version_release_plan DROP CONSTRAINT ck_mk_version_release_plan_status;
ALTER TABLE mk_version_release_plan ADD CONSTRAINT ck_mk_version_release_plan_status
    CHECK (status IN ('IN_REVIEW','REJECTED','APPROVED','PUBLISHED','GRAY','PAUSED','ROLLED_BACK','FAILED'));
ALTER TABLE mk_engine_notification DROP CONSTRAINT ck_notification_source_type;
ALTER TABLE mk_engine_notification ADD CONSTRAINT ck_notification_source_type
    CHECK (source_type IN ('FOLLOWUP_EVENT','SAFETY_REVIEW','WORKFLOW_TODO','SYNC_EVENT',
        'RULE_EVENT','PATHWAY_EVENT','RELEASE_ROLLOUT'));

CREATE TABLE mk_version_rollout_observation (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    observation_id VARCHAR2(64) NOT NULL,
    plan_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    stage_index INTEGER NOT NULL,
    sample_count BIGINT NOT NULL,
    hit_count BIGINT NOT NULL,
    block_count BIGINT NOT NULL,
    manual_rejection_count BIGINT NOT NULL,
    anomaly_count BIGINT NOT NULL,
    hit_rate DECIMAL(9,6) NOT NULL,
    block_rate DECIMAL(9,6) NOT NULL,
    manual_rejection_rate DECIMAL(9,6) NOT NULL,
    anomaly_rate DECIMAL(9,6) NOT NULL,
    observed_at TIMESTAMP NOT NULL,
    created_by VARCHAR2(128) NOT NULL,
    trace_id VARCHAR2(128) NULL,
    CONSTRAINT uk_mk_version_rollout_observation_id UNIQUE (observation_id),
    CONSTRAINT ck_mk_version_rollout_observation_counts CHECK (
        stage_index >= 0 AND sample_count > 0 AND hit_count >= 0 AND block_count >= 0
        AND manual_rejection_count >= 0 AND anomaly_count >= 0),
    CONSTRAINT ck_mk_version_rollout_observation_rates CHECK (
        hit_rate BETWEEN 0 AND 1 AND block_rate BETWEEN 0 AND 1
        AND manual_rejection_rate BETWEEN 0 AND 1 AND anomaly_rate BETWEEN 0 AND 1)
);
CREATE INDEX idx_mk_version_rollout_plan
    ON mk_version_rollout_observation (tenant_id, plan_id, stage_index, observed_at);

CREATE TABLE mk_version_override_template (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    template_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    template_name VARCHAR2(200) NOT NULL,
    description VARCHAR2(1000) NULL,
    applicable_scope VARCHAR2(1000) NOT NULL,
    status VARCHAR2(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR2(128) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR2(128) NOT NULL,
    trace_id VARCHAR2(128) NULL,
    CONSTRAINT uk_mk_version_override_template_id UNIQUE (template_id),
    CONSTRAINT uk_mk_version_override_template_name UNIQUE (tenant_id, template_name),
    CONSTRAINT ck_mk_version_override_template_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_mk_version_override_template_tenant
    ON mk_version_override_template (tenant_id, status, updated_at);

CREATE TABLE mk_version_override_template_item (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    item_id VARCHAR2(64) NOT NULL,
    template_id VARCHAR2(64) NOT NULL,
    asset_type VARCHAR2(32) NOT NULL,
    asset_identity VARCHAR2(200) NOT NULL,
    inherited_version_id VARCHAR2(64) NULL,
    source_override_version_id VARCHAR2(64) NULL,
    override_mode VARCHAR2(16) NOT NULL,
    propagation VARCHAR2(16) NOT NULL,
    applicable_scope VARCHAR2(1000) NOT NULL,
    diff_summary VARCHAR2(2000) NOT NULL,
    override_reason VARCHAR2(1000) NOT NULL,
    CONSTRAINT uk_mk_version_override_template_item_id UNIQUE (item_id),
    CONSTRAINT uk_mk_version_override_template_asset UNIQUE (template_id, asset_type, asset_identity),
    CONSTRAINT ck_mk_version_override_template_mode CHECK (override_mode IN ('REPLACE','DISABLE','ADD')),
    CONSTRAINT ck_mk_version_override_template_propagation CHECK (propagation IN ('INHERITABLE','EXCLUSIVE'))
);

CREATE TABLE mk_version_override_operation (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    operation_id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(64) NOT NULL,
    operation_type VARCHAR2(16) NOT NULL,
    template_id VARCHAR2(64) NULL,
    source_org_unit_id VARCHAR2(64) NULL,
    target_org_units_json CLOB NOT NULL,
    status VARCHAR2(16) NOT NULL,
    preview_digest VARCHAR2(64) NOT NULL,
    result_summary_json CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR2(128) NOT NULL,
    trace_id VARCHAR2(128) NULL,
    CONSTRAINT uk_mk_version_override_operation_id UNIQUE (operation_id),
    CONSTRAINT ck_mk_version_override_operation_type CHECK (operation_type IN ('APPLY','REVOKE','CLONE')),
    CONSTRAINT ck_mk_version_override_operation_status CHECK (status IN ('PREVIEWED','APPLIED','REVOKED','FAILED'))
);
CREATE INDEX idx_mk_version_override_operation_tenant
    ON mk_version_override_operation (tenant_id, operation_type, created_at);

COMMENT ON COLUMN mk_version_release_plan.rollout_strategy IS '发布放量策略，与组织作用域独立';
COMMENT ON COLUMN mk_version_release_plan.rollout_config_json IS '结构化灰度策略参数 JSON';
COMMENT ON COLUMN mk_version_release_plan.rollout_stage_index IS '当前已进入的灰度批次下标';
COMMENT ON COLUMN mk_version_release_plan.rollout_paused_reason IS '自动或人工暂停放量的原因';
COMMENT ON TABLE mk_version_rollout_observation IS '灰度批次关键指标观测事实';
COMMENT ON TABLE mk_version_override_template IS '可复用的组织覆盖模板';
COMMENT ON TABLE mk_version_override_template_item IS '覆盖模板内的资产覆盖项';
COMMENT ON TABLE mk_version_override_operation IS '覆盖模板批量预演、生效、撤销和克隆记录';
