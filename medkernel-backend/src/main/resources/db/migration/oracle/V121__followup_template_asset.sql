-- FOLLOW-01：随访模板配置资产及运行计划版本追溯（Oracle）。
-- 回滚：确认无计划引用后删除索引与 followup_plan/followup_task 新增字段，再删除 mk_followup_template。

CREATE TABLE mk_followup_template (
    id                            NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id                   VARCHAR2(64)  NOT NULL,
    tenant_id                     VARCHAR2(64)  NOT NULL,
    template_code                 VARCHAR2(128) NOT NULL,
    version_no                    NUMBER(10)    NOT NULL,
    name                          VARCHAR2(200) NOT NULL,
    description                   VARCHAR2(1000) NULL,
    organization_scope            VARCHAR2(1000) NOT NULL,
    applicable_scope              VARCHAR2(512) NOT NULL,
    task_definition_json          CLOB          NOT NULL,
    questionnaire_definition_json CLOB          NOT NULL,
    abnormal_action_json          CLOB          NOT NULL,
    source_ref                    VARCHAR2(1000) NOT NULL,
    asset_version_id              VARCHAR2(64)  NOT NULL,
    created_at                    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    created_by                    VARCHAR2(64)  NOT NULL,
    updated_at                    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_by                    VARCHAR2(64)  NOT NULL,
    trace_id                      VARCHAR2(128) NOT NULL,
    CONSTRAINT uk_mk_followup_template_id UNIQUE (template_id),
    CONSTRAINT uk_mk_followup_template_code_version UNIQUE (tenant_id, template_code, version_no),
    CONSTRAINT uk_mk_followup_template_asset_version UNIQUE (asset_version_id),
    CONSTRAINT ck_mk_followup_template_version CHECK (version_no > 0)
);

ALTER TABLE followup_plan ADD (
    template_id VARCHAR2(64) NULL,
    template_version NUMBER(10) NULL
);
ALTER TABLE followup_task ADD questionnaire_template_id VARCHAR2(128) NULL;

CREATE INDEX idx_mk_followup_template_tenant
    ON mk_followup_template (tenant_id, updated_at);
CREATE INDEX idx_followup_plan_template
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
