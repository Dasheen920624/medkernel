-- FOLLOW-01 PR1：随访计划生成来源事实与关键时钟追溯字段。
-- ROLLBACK: 删除 idx_followup_task_clock、idx_followup_plan_fact 索引后，删除 followup_task.clinical_clock_id 与 followup_plan.source_fact_type/source_fact_id/generation_rule_code/generation_explanation 字段；已生成计划须按审计策略归档后处理。

ALTER TABLE followup_plan ADD COLUMN IF NOT EXISTS source_fact_type VARCHAR(32) NULL;
ALTER TABLE followup_plan ADD COLUMN IF NOT EXISTS source_fact_id VARCHAR(128) NULL;
ALTER TABLE followup_plan ADD COLUMN IF NOT EXISTS generation_rule_code VARCHAR(96) NULL;
ALTER TABLE followup_plan ADD COLUMN IF NOT EXISTS generation_explanation CLOB NULL;

ALTER TABLE followup_task ADD COLUMN IF NOT EXISTS clinical_clock_id VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_followup_plan_fact
    ON followup_plan (tenant_id, source_fact_type, source_fact_id);

CREATE INDEX IF NOT EXISTS idx_followup_task_clock
    ON followup_task (tenant_id, clinical_clock_id);

COMMENT ON COLUMN followup_plan.source_fact_type IS '随访计划生成来源事实类型：PATHWAY路径、DIAGNOSIS诊断或RISK风险分层';
COMMENT ON COLUMN followup_plan.source_fact_id IS '随访计划生成来源事实业务ID，如患者路径实例ID、诊断编码或风险分层值';
COMMENT ON COLUMN followup_plan.generation_rule_code IS '随访计划确定性生成规则编码，用于复现与审计';
COMMENT ON COLUMN followup_plan.generation_explanation IS '随访计划生成解释JSON，记录受控事实、任务类型、模型状态和关键时钟依据';
COMMENT ON COLUMN followup_task.clinical_clock_id IS '随访任务绑定的路径关键时钟ID，用于到期追溯';
