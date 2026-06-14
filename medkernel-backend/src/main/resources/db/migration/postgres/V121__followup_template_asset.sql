-- FOLLOW-01：随访模板配置资产及运行计划版本追溯（PostgreSQL）。
-- 回滚：确认无计划引用后删除索引与 followup_plan/followup_task 新增字段，再删除 mk_followup_template。

CREATE TABLE IF NOT EXISTS mk_followup_template (
    id                            BIGSERIAL    PRIMARY KEY,
    template_id                   VARCHAR(64)  NOT NULL,
    tenant_id                     VARCHAR(64)  NOT NULL,
    template_code                 VARCHAR(128) NOT NULL,
    version_no                    INTEGER      NOT NULL,
    name                          VARCHAR(200) NOT NULL,
    description                   VARCHAR(1000) NULL,
    organization_scope            VARCHAR(1000) NOT NULL,
    applicable_scope              VARCHAR(512) NOT NULL,
    task_definition_json          TEXT         NOT NULL,
    questionnaire_definition_json TEXT         NOT NULL,
    abnormal_action_json          TEXT         NOT NULL,
    source_ref                    VARCHAR(1000) NOT NULL,
    asset_version_id              VARCHAR(64)  NOT NULL,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                    VARCHAR(64)  NOT NULL,
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by                    VARCHAR(64)  NOT NULL,
    trace_id                      VARCHAR(128) NOT NULL,
    CONSTRAINT uk_mk_followup_template_id UNIQUE (template_id),
    CONSTRAINT uk_mk_followup_template_code_version UNIQUE (tenant_id, template_code, version_no),
    CONSTRAINT uk_mk_followup_template_asset_version UNIQUE (asset_version_id),
    CONSTRAINT ck_mk_followup_template_version CHECK (version_no > 0)
);

ALTER TABLE followup_plan ADD COLUMN IF NOT EXISTS template_id VARCHAR(64) NULL;
ALTER TABLE followup_plan ADD COLUMN IF NOT EXISTS template_version INTEGER NULL;
ALTER TABLE followup_task ADD COLUMN IF NOT EXISTS questionnaire_template_id VARCHAR(128) NULL;

CREATE INDEX IF NOT EXISTS idx_mk_followup_template_tenant
    ON mk_followup_template (tenant_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_followup_plan_template
    ON followup_plan (tenant_id, template_id, template_version);

COMMENT ON TABLE mk_followup_template IS '随访配置模板不可变版本，不保存患者、就诊和问卷作答等运行数据';
COMMENT ON COLUMN mk_followup_template.template_id IS '随访模板稳定业务ID';
COMMENT ON COLUMN mk_followup_template.template_code IS '随访模板业务编码';
COMMENT ON COLUMN mk_followup_template.version_no IS '随访模板不可变版本号';
COMMENT ON COLUMN mk_followup_template.task_definition_json IS '任务类型、相对时点和问卷模板绑定JSON';
COMMENT ON COLUMN mk_followup_template.questionnaire_definition_json IS '问卷结构定义JSON，不含真实作答';
COMMENT ON COLUMN mk_followup_template.abnormal_action_json IS '异常触发条件、回院动作和通知目标JSON';
COMMENT ON COLUMN mk_followup_template.asset_version_id IS '统一配置资产版本ID';
COMMENT ON COLUMN followup_plan.template_id IS '生成计划所绑定的已发布随访模板ID';
COMMENT ON COLUMN followup_plan.template_version IS '生成计划所绑定的随访模板版本号';
COMMENT ON COLUMN followup_task.questionnaire_template_id IS '问卷任务绑定的问卷模板ID';
